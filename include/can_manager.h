#ifndef CAN_MANAGER_H
#define CAN_MANAGER_H

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

#define CAN_MANAGER_MAX_SUBSCRIBERS 8
#define CAN_MANAGER_MAX_DATA_LENGTH 8

typedef struct
{
    uint32_t id;
    uint8_t dlc;
    bool is_extended;
    bool is_remote;
    uint8_t data[CAN_MANAGER_MAX_DATA_LENGTH];
} can_frame_t;

typedef void (*can_manager_frame_callback_t)(
    const can_frame_t *frame,
    void *context);

esp_err_t can_manager_init(void);

esp_err_t can_manager_subscribe(
    uint32_t id,
    bool is_extended,
    can_manager_frame_callback_t callback,
    void *context);

#endif