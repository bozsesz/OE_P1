#include "can_manager.h"

#include <inttypes.h>
#include <string.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_twai.h"
#include "esp_twai_onchip.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

#define CAN_TX_GPIO GPIO_NUM_1
#define CAN_RX_GPIO GPIO_NUM_0
#define CAN_RX_QUEUE_LENGTH 32

static const char *TAG = "CAN_MANAGER";

typedef struct
{
    uint32_t id;
    bool is_extended;
    can_manager_frame_callback_t callback;
    void *context;
} can_subscription_t;

static twai_node_handle_t s_node;
static QueueHandle_t s_rx_queue;

static portMUX_TYPE s_subscriptions_lock = portMUX_INITIALIZER_UNLOCKED;
static can_subscription_t s_subscriptions[CAN_MANAGER_MAX_SUBSCRIBERS];
static size_t s_subscription_count;

static portMUX_TYPE s_dropped_frames_lock = portMUX_INITIALIZER_UNLOCKED;
static uint32_t s_dropped_frames;

static bool can_rx_done_callback(
    twai_node_handle_t node,
    const twai_rx_done_event_data_t *event,
    void *context)
{
    (void)event;
    (void)context;

    uint8_t data[CAN_MANAGER_MAX_DATA_LENGTH] = {0};
    twai_frame_t rx_frame = {
        .buffer = data,
        .buffer_len = sizeof(data),
    };

    if (twai_node_receive_from_isr(node, &rx_frame) != ESP_OK)
    {
        return false;
    }

    can_frame_t frame = {
        .id = rx_frame.header.id,
        .dlc = rx_frame.header.dlc,
        .is_extended = rx_frame.header.ide,
        .is_remote = rx_frame.header.rtr,
    };

    if (frame.dlc > CAN_MANAGER_MAX_DATA_LENGTH)
    {
        portENTER_CRITICAL_ISR(&s_dropped_frames_lock);
        s_dropped_frames++;
        portEXIT_CRITICAL_ISR(&s_dropped_frames_lock);
        return false;
    }

    if (!frame.is_remote)
    {
        memcpy(frame.data, data, frame.dlc);
    }

    BaseType_t higher_priority_task_woken = pdFALSE;

    if (xQueueSendFromISR(
            s_rx_queue,
            &frame,
            &higher_priority_task_woken) != pdPASS)
    {
        portENTER_CRITICAL_ISR(&s_dropped_frames_lock);
        s_dropped_frames++;
        portEXIT_CRITICAL_ISR(&s_dropped_frames_lock);
    }

    return higher_priority_task_woken == pdTRUE;
}

static void dispatch_frame(const can_frame_t *frame)
{
    can_subscription_t subscriptions[CAN_MANAGER_MAX_SUBSCRIBERS];
    size_t count;

    portENTER_CRITICAL(&s_subscriptions_lock);
    count = s_subscription_count;
    memcpy(subscriptions, s_subscriptions, count * sizeof(subscriptions[0]));
    portEXIT_CRITICAL(&s_subscriptions_lock);

    for (size_t i = 0; i < count; i++)
    {
        if (subscriptions[i].id == frame->id &&
            subscriptions[i].is_extended == frame->is_extended)
        {
            subscriptions[i].callback(frame, subscriptions[i].context);
        }
    }
}

static void can_receive_task(void *argument)
{
    (void)argument;

    while (true)
    {
        can_frame_t frame;

        if (xQueueReceive(s_rx_queue, &frame, pdMS_TO_TICKS(1000)) == pdTRUE)
        {
            ESP_LOGI(TAG, "RX %s ID=0x%" PRIX32 " DLC=%u",
                     frame.is_extended ? "EXT" : "STD",
                     frame.id,
                     (unsigned)frame.dlc);

            if (frame.dlc > 0 && !frame.is_remote)
            {
                ESP_LOG_BUFFER_HEX(TAG, frame.data, frame.dlc);
            }

            dispatch_frame(&frame);
        }

        portENTER_CRITICAL(&s_dropped_frames_lock);
        uint32_t dropped = s_dropped_frames;
        s_dropped_frames = 0;
        portEXIT_CRITICAL(&s_dropped_frames_lock);

        if (dropped > 0)
        {
            ESP_LOGW(TAG, "Dropped %" PRIu32 " CAN frame(s)", dropped);
        }
    }
}

esp_err_t can_manager_subscribe(
    uint32_t id,
    bool is_extended,
    can_manager_frame_callback_t callback,
    void *context)
{
    uint32_t max_id = is_extended ? 0x1FFFFFFF : 0x7FF;

    if (callback == NULL || id > max_id)
    {
        return ESP_ERR_INVALID_ARG;
    }

    portENTER_CRITICAL(&s_subscriptions_lock);

    if (s_subscription_count >= CAN_MANAGER_MAX_SUBSCRIBERS)
    {
        portEXIT_CRITICAL(&s_subscriptions_lock);
        return ESP_ERR_NO_MEM;
    }

    s_subscriptions[s_subscription_count++] = (can_subscription_t){
        .id = id,
        .is_extended = is_extended,
        .callback = callback,
        .context = context,
    };

    portEXIT_CRITICAL(&s_subscriptions_lock);

    ESP_LOGI(TAG, "Subscribed to %s ID=0x%" PRIX32,
             is_extended ? "extended" : "standard", id);

    return ESP_OK;
}

esp_err_t can_manager_init(void)
{
    twai_onchip_node_config_t config = {
        .io_cfg = {
            .tx = CAN_TX_GPIO,
            .rx = CAN_RX_GPIO,
            .quanta_clk_out = GPIO_NUM_NC,
            .bus_off_indicator = GPIO_NUM_NC,
        },
        .bit_timing = {
            .bitrate = 500000,
            .sp_permill = 800,
        },
    };

    twai_event_callbacks_t callbacks = {
        .on_rx_done = can_rx_done_callback,
    };

    s_rx_queue = xQueueCreate(CAN_RX_QUEUE_LENGTH, sizeof(can_frame_t));
    if (s_rx_queue == NULL)
    {
        ESP_LOGE(TAG, "Could not create CAN receive queue");
        return ESP_ERR_NO_MEM;
    }

    esp_err_t ret = twai_new_node_onchip(&config, &s_node);
    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Could not create TWAI node: %s", esp_err_to_name(ret));
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return ret;
    }

    ret = twai_node_register_event_callbacks(s_node, &callbacks, NULL);
    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Could not register TWAI callbacks: %s", esp_err_to_name(ret));
        twai_node_delete(s_node);
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return ret;
    }

    ret = twai_node_enable(s_node);
    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Could not enable TWAI node: %s", esp_err_to_name(ret));
        twai_node_delete(s_node);
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return ret;
    }

    if (xTaskCreate(can_receive_task, "can_rx", 4096, NULL, 5, NULL) != pdPASS)
    {
        ESP_LOGE(TAG, "Could not create CAN receive task");
        twai_node_disable(s_node);
        twai_node_delete(s_node);
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return ESP_ERR_NO_MEM;
    }

    ESP_LOGI(TAG, "CAN receive started at 500 kbit/s");
    return ESP_OK;
}