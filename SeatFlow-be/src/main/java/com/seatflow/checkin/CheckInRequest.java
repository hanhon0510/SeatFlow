package com.seatflow.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the scanner read: either the QR payload or the bare ticket code typed in by hand. */
public record CheckInRequest(@NotBlank @Size(max = 200) String code) {
}
