package com.example.ums.subscription.dto;

public record SubscriptionDto(String id, String type, String value, String frequency, String channel, Boolean enabled) {}
