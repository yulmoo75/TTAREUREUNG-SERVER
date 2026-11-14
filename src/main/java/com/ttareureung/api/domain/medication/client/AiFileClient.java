package com.ttareureung.api.domain.medication.client;

public interface AiFileClient {
    String askWithFile(String system, String mediaType, byte[] data, String prompt);
}
