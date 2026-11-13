package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InvitationResponse(@JsonProperty("invited_count") int invitedCount, String message) {

}
