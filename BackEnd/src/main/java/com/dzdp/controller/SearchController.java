package com.dzdp.controller;

import com.dzdp.dto.Result;
import com.dzdp.ratelimit.RateLimit;
import com.dzdp.ratelimit.RateLimitDimension;
import com.dzdp.ratelimit.RateLimitRule;
import com.dzdp.service.ISearchService;
import com.dzdp.trace.ApiTrace;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@ApiTrace
@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private ISearchService searchService;

    @GetMapping("/suggestions")
    @RateLimit(@RateLimitRule(key = "search:suggestions", dimension = RateLimitDimension.IP,
            capacity = 30, refillTokens = 20, refillPeriodMillis = 1_000L))
    public Result querySuggestions(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "requestId", required = false) Long requestId
    ) {
        return searchService.querySuggestions(keyword, requestId);
    }

    @GetMapping("/results")
    @RateLimit(@RateLimitRule(key = "search:results", dimension = RateLimitDimension.IP,
            capacity = 20, refillTokens = 10, refillPeriodMillis = 1_000L))
    public Result queryResults(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false, defaultValue = "ALL") String type
    ) {
        return searchService.queryResults(keyword, type);
    }
}
