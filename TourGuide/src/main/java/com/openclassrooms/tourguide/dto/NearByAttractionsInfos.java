package com.openclassrooms.tourguide.dto;

import java.util.List;

public record NearByAttractionsInfos(
        double userLocationLongitude,
        double userLocationLatitude,
        List<AttractionNearTheUser> attractionsNearTheUser) {
}
