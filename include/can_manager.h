#ifndef CAN_MANAGER_H
#define CAN_MANAGER_H

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"

#define CAN_MANAGER_DATA_SIZE 8

typedef struct
{
    uint32_t id;
    uint8_t length;
    bool extended;
    bool remote;
    uint8_t data[CAN_MANAGER_DATA_SIZE];
} can_frame_t;

esp_err_t can_manager_init(void);

esp_err_t can_manager_send(
    uint32_t id,
    const uint8_t *data,
    uint8_t length);

esp_err_t can_manager_receive(
    can_frame_t *frame,
    uint32_t timeout_ms);

#endif