#include "ble_manager.h"
#include "esp_log.h"
#include "nvs_flash.h"

// NimBLE kötelező fejlécek
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

static const char *TAG = "BLE_MANAGER";

// Egyedi 128-bites UUID-k a Szolgáltatáshoz és a Karakterisztikához
// Custom Service UUID: 12345678-1234-5678-1234-56789abcdef0
static const ble_uuid128_t gatt_service_uuid =
    BLE_UUID128_INIT(0xf0, 0xde, 0xbc, 0x9a, 0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12);

// Custom Characteristic UUID (PT100 Data): 12345678-1234-5678-1234-56789abcdef1
static const ble_uuid128_t gatt_char_uuid =
    BLE_UUID128_INIT(0xf1, 0xde, 0xbc, 0x9a, 0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12);

static uint16_t s_pt100_char_val_handle;
static uint16_t s_conn_handle = BLE_HS_CONN_HANDLE_NONE;

static int ble_gap_event_cb(struct ble_gap_event *event, void *arg);

// GAP Hirdetés elindítása
static void ble_app_advertise(void)
{
    struct ble_hs_adv_fields fields;
    memset(&fields, 0, sizeof(fields));

    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.name = (uint8_t *)"ESP32-C3 Super Mini";
    fields.name_len = strlen("ESP32-C3 Super Mini");
    fields.name_is_complete = 1;

    ble_gap_adv_set_fields(&fields);

    struct ble_gap_adv_params adv_params;
    memset(&adv_params, 0, sizeof(adv_params));
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;

    ble_gap_adv_start(0, NULL, BLE_HS_FOREVER, &adv_params, ble_gap_event_cb, NULL);
    ESP_LOGI(TAG, "BLE hirdetes elinditva: ESP32-C3 Super Mini");
}

// GAP Eseménykezelő (Csatlakozás / Szétkapcsolás)
static int ble_gap_event_cb(struct ble_gap_event *event, void *arg)
{
    switch (event->type)
    {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0)
        {
            ESP_LOGI(TAG, "Kliens csatlakozott! Handle: %d", event->connect.conn_handle);
            s_conn_handle = event->connect.conn_handle;
        }
        else
        {
            ble_app_advertise();
        }
        break;

    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI(TAG, "Kliens lecsatlakozott. Újrahirdetés...");
        s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
        ble_app_advertise();
        break;

    default:
        break;
    }
    return 0;
}

// GATT Karakterisztika Olvasási/Írási Callback
static int gatt_svr_chr_access_cb(uint16_t conn_handle, uint16_t attr_handle,
                                  struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    // Olvasási kérés esetén a mentett adat visszaküldése
    return 0;
}

// GATT Szolgáltatások regisztrációja
static const struct ble_gatt_svc_def gatt_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &gatt_service_uuid.u,
        .characteristics = (struct ble_gatt_chr_def[]){
            {
                .uuid = &gatt_char_uuid.u,
                .access_cb = gatt_svr_chr_access_cb,
                .val_handle = &s_pt100_char_val_handle,
                .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
            },
            {0} // Karakterisztikák lezárása
        },
    },
    {0} // Szolgáltatások lezárása
};

// NimBLE Host Sync Callback (Amikor a Bluetooth készen áll)
static void ble_on_sync(void)
{
    ble_hs_id_infer_auto(0, NULL);
    ble_app_advertise();
}

// NimBLE Task futtatása FreeRTOS alatt
static void nimble_host_task(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
}

esp_err_t ble_manager_init(void)
{
    // 1. NVS inicializálás (Szükséges a BLE-hez!)
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    if (ret != ESP_OK)
        return ret;

    // 2. NimBLE port inicializálása
    ret = nimble_port_init();
    if (ret != ESP_OK)
        return ret;

    // 3. Alapértelmezett GAP és GATT szolgáltatások
    ble_svc_gap_init();
    ble_svc_gatt_init();

    // 4. Egyedi GATT szolgáltatás regisztrálása
    int res = ble_gatts_count_cfg(gatt_svcs);
    if (res != 0)
        return ESP_FAIL;

    res = ble_gatts_add_svcs(gatt_svcs);
    if (res != 0)
        return ESP_FAIL;

    // 5. Host Sync Callback beállítása
    ble_hs_cfg.sync_cb = ble_on_sync;

    // 6. NimBLE FreeRTOS task elindítása
    nimble_port_freertos_init(nimble_host_task);

    ESP_LOGI(TAG, "BLE Manager sikeresen inicializalva.");
    return ESP_OK;
}

esp_err_t ble_manager_update_data(const ble_pt100_data_t *data)
{
    if (!data)
        return ESP_ERR_INVALID_ARG;

    // Ha van csatlakozott kliens, küldünk egy Notify csomagot
    if (s_conn_handle != BLE_HS_CONN_HANDLE_NONE)
    {
        struct os_mbuf *om = ble_hs_mbuf_from_flat(data, sizeof(ble_pt100_data_t));
        if (om)
        {
            ble_gatts_notify_custom(s_conn_handle, s_pt100_char_val_handle, om);
            ESP_LOGD(TAG, "PT100 adatok elkuldve BLE Notify-al.");
        }
    }

    return ESP_OK;
}