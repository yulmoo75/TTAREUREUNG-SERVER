package com.ttareureung.api.domain.guardian.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AcceptInvitationResponse(@JsonProperty("senior_profile_id") Long seniorProfileId) {

}
