package com.openclassrooms.tourguide.dto;

public record AttractionNearTheUser(
        String attractionName,
        double attractionLongitude,
        double attractionLatitude,
        double distanceFromUserLocation,
        int attractionRewardPoints) {
}
