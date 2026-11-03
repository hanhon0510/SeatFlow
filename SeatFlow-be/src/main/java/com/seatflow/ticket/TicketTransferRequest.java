package com.seatflow.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TicketTransferRequest(@NotBlank @Email String recipientEmail) {
}
