#include "can_manager.h"

#include <string.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_twai.h"
#include "esp_twai_onchip.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"

#define CAN_TX_GPIO GPIO_NUM_1
#define CAN_RX_GPIO GPIO_NUM_0
#define CAN_RX_QUEUE_LENGTH 16

static const char *TAG = "CAN";
static twai_node_handle_t s_node;
static QueueHandle_t s_rx_queue;

static bool can_rx_callback(
    twai_node_handle_t node,
    const twai_rx_done_event_data_t *event,
    void *context)
{
    (void)event;
    (void)context;

    uint8_t data[CAN_MANAGER_DATA_SIZE] = {0};
    twai_frame_t rx = {
        .buffer = data,
        .buffer_len = sizeof(data),
    };

    if (twai_node_receive_from_isr(node, &rx) != ESP_OK)
    {
        return false;
    }

    if (rx.header.dlc > CAN_MANAGER_DATA_SIZE)
    {
        return false;
    }

    can_frame_t frame = {
        .id = rx.header.id,
        .length = rx.header.dlc,
        .extended = rx.header.ide,
        .remote = rx.header.rtr,
    };

    if (!frame.remote)
    {
        memcpy(frame.data, data, frame.length);
    }

    BaseType_t task_woken = pdFALSE;
    if (xQueueSendFromISR(s_rx_queue, &frame, &task_woken) != pdPASS)
    {
        ESP_EARLY_LOGW(TAG, "RX queue full; dropping CAN frame");
    }

    return task_woken == pdTRUE;
}

esp_err_t can_manager_init(void)
{
    s_rx_queue = xQueueCreate(CAN_RX_QUEUE_LENGTH, sizeof(can_frame_t));
    if (s_rx_queue == NULL)
    {
        return ESP_ERR_NO_MEM;
    }

    const twai_onchip_node_config_t config = {
        .io_cfg = {
            .tx = CAN_TX_GPIO,
            .rx = CAN_RX_GPIO,
            .quanta_clk_out = GPIO_NUM_NC,
            .bus_off_indicator = GPIO_NUM_NC,
        },
        .bit_timing = {
            .bitrate = 500000, // Placeholder: must match the bus.
            .sp_permill = 800,
        },
        .tx_queue_depth = 5,
    };

    esp_err_t err = twai_new_node_onchip(&config, &s_node);
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "TWAI node setup failed: %s", esp_err_to_name(err));
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return err;
    }

    const twai_event_callbacks_t callbacks = {
        .on_rx_done = can_rx_callback,
    };

    err = twai_node_register_event_callbacks(s_node, &callbacks, NULL);
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "TWAI callback setup failed: %s", esp_err_to_name(err));
        twai_node_delete(s_node);
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return err;
    }

    err = twai_node_enable(s_node);
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "TWAI enable failed: %s", esp_err_to_name(err));
        twai_node_delete(s_node);
        vQueueDelete(s_rx_queue);
        s_rx_queue = NULL;
        return err;
    }

    ESP_LOGI(TAG, "CAN started");
    return ESP_OK;
}

esp_err_t can_manager_send(
    uint32_t id,
    const uint8_t *data,
    uint8_t length)
{
    if (id > 0x7FF || length > CAN_MANAGER_DATA_SIZE ||
        (length > 0 && data == NULL))
    {
        return ESP_ERR_INVALID_ARG;
    }

    twai_frame_t tx = {
        .header = {
            .id = id,
            .dlc = length,
        },
        .buffer = (uint8_t *)data,
        .buffer_len = length,
    };

    return twai_node_transmit(s_node, &tx, 100);
}

esp_err_t can_manager_receive(
    can_frame_t *frame,
    uint32_t timeout_ms)
{
    if (frame == NULL || s_rx_queue == NULL)
    {
        return ESP_ERR_INVALID_ARG;
    }

    if (xQueueReceive(
            s_rx_queue,
            frame,
            pdMS_TO_TICKS(timeout_ms)) == pdTRUE)
    {
        return ESP_OK;
    }

    return ESP_ERR_TIMEOUT;
}