package com.dzdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {
    private String type;
    private Long sourceId;
    private Long shopId;
    private String title;
    private String subtitle;
    private String description;
    private String image;
}
