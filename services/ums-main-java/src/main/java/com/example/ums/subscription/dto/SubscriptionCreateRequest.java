package com.example.ums.subscription.dto;

public record SubscriptionCreateRequest(String type, String value, String frequency, String channel, Boolean enabled) {}
