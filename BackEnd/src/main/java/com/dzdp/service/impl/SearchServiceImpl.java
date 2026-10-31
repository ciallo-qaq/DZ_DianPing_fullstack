package com.dzdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.dzdp.dto.Result;
import com.dzdp.dto.SearchResultDTO;
import com.dzdp.dto.SearchSuggestionDTO;
import com.dzdp.dto.SearchSuggestionResponse;
import com.dzdp.dto.StatusCode;
import com.dzdp.entity.Blog;
import com.dzdp.entity.Shop;
import com.dzdp.entity.Voucher;
import com.dzdp.service.IBlogService;
import com.dzdp.service.ISearchService;
import com.dzdp.service.IShopService;
import com.dzdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.lang.model.util.ElementScanner6;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.dzdp.utils.RedisConstants.SEARCH_SUGGESTION_EMPTY_TTL;
import static com.dzdp.utils.RedisConstants.SEARCH_SUGGESTION_KEY;
import static com.dzdp.utils.RedisConstants.SEARCH_SUGGESTION_TTL;

@Service
public class SearchServiceImpl implements ISearchService {
//todo
//    1.blog content字段拆分：正文，图片url存储MySQL，图片，视频存储OSS/MinIO
//    2.请求乱序问题：请求附带递增RequestId，仅最新请求的响应允许更新页面
//    3.关联过程中，如何从大量blog查询含有keyword的blog：N-gram算法/ES
//    4.完整链路耗时过长，如何实现近实时？
//    5.评分模型定义权重，返回评分高的几条。

    private static final int MAX_KEYWORD_LENGTH = 10;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int MAX_RESULTS = 10;
    private static final int MIN_NGRAM_CONTENT_LENGTH = 2;

    @Resource
    private IShopService shopService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result querySuggestions(String keyword, Long requestId) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Result validationResult = validateKeyword(normalizedKeyword);
        if (validationResult != null) {
            return validationResult;
        }

        String cacheKey = SEARCH_SUGGESTION_KEY
                + normalizedKeyword.toLowerCase(Locale.ROOT);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        List<SearchSuggestionDTO> suggestions;

        if (cached != null) {
            suggestions = JSONUtil.toList(cached, SearchSuggestionDTO.class);
        } else {
            suggestions = buildSuggestions(normalizedKeyword);
            long ttl = suggestions.isEmpty() ? SEARCH_SUGGESTION_EMPTY_TTL : SEARCH_SUGGESTION_TTL;
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    JSONUtil.toJsonStr(suggestions),
                    ttl,
                    TimeUnit.SECONDS
            );
        }

        SearchSuggestionResponse response =
                new SearchSuggestionResponse(normalizedKeyword, requestId, suggestions);
        return Result.ok(response);
    }

    @Override
    public Result queryResults(String keyword, String type) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Result validationResult = validateKeyword(normalizedKeyword);
        if (validationResult != null) {
            return validationResult;
        }

        String normalizedType = StrUtil.isBlank(type)
                ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        if (!isSupportedType(normalizedType)) {
            return Result.fail(StatusCode.INVALID_PARAM, "搜索结果类型不支持");
        }

        List<Shop> shops = shouldQuery(normalizedType, "SHOP")
                ? queryShops(normalizedKeyword, MAX_RESULTS) : Collections.emptyList();
        List<Voucher> vouchers = shouldQuery(normalizedType, "VOUCHER")
                ? queryVouchers(normalizedKeyword, MAX_RESULTS) : Collections.emptyList();
        List<Blog> blogs = shouldQuery(normalizedType, "BLOG")
                ? queryBlogs(normalizedKeyword, MAX_RESULTS) : Collections.emptyList();

        Map<Long, Shop> shopMap = loadShopMap(vouchers, blogs);
        List<SearchResultDTO> results = new ArrayList<>();
        for (Shop shop : shops) {
            results.add(toShopResult(shop));
        }
        for (Voucher voucher : vouchers) {
            results.add(toVoucherResult(voucher, shopMap.get(voucher.getShopId())));
        }
        for (Blog blog : blogs) {
            results.add(toBlogResult(blog, shopMap.get(blog.getShopId())));
        }

        List<SearchResultDTO> limited = results.size() > MAX_RESULTS
                ? new ArrayList<>(results.subList(0, MAX_RESULTS))
                : results;
        return Result.ok(limited);
    }



    private List<SearchSuggestionDTO> buildSuggestions(String keyword) {
        List<Shop> shops = queryShops(keyword, MAX_SUGGESTIONS);
        List<Voucher> vouchers = queryVouchers(keyword, MAX_SUGGESTIONS);
        List<Blog> blogs = queryBlogs(keyword, MAX_SUGGESTIONS);
        Map<Long, Shop> shopMap = loadShopMap(vouchers, blogs);

        List<SearchSuggestionDTO> suggestions = new ArrayList<>(MAX_SUGGESTIONS);
        Set<String> seen = new HashSet<>();
        for (Shop shop : shops) {
            if (addSuggestion(suggestions, seen, new SearchSuggestionDTO(
                    shop.getName(),
                    "SHOP",
                    shop.getId(),
                    shop.getName(),
                    prependIfPresent("店铺", shop.getArea()),
                    "PREFIX"
            ))) {
                break;
            }
        }
        for (Voucher voucher : vouchers) {
            if (addSuggestion(suggestions, seen, new SearchSuggestionDTO(
                    voucher.getTitle(),
                    "VOUCHER",
                    voucher.getId(),
                    voucher.getTitle(),
                    voucherSubtitle(voucher, shopMap.get(voucher.getShopId())),
                    "PREFIX"
            ))) {
                break;
            }
        }
        for (Blog blog : blogs) {
            boolean titlePrefix = startsWithIgnoreCase(blog.getTitle(), keyword);
            String shopName = shopName(shopMap.get(blog.getShopId()));
            if (addSuggestion(suggestions, seen, new SearchSuggestionDTO(
                    titlePrefix ? blog.getTitle() : keyword,
                    "BLOG",
                    blog.getId(),
                    blog.getTitle(),
                    prependIfPresent(
                            "笔记",
                            joinPresent(shopName, plainText(blog.getContent(), 36))
                    ),
                    titlePrefix ? "PREFIX" : "CONTENT"
            ))) {
                break;
            }
        }
        return suggestions;
    }



    private boolean addSuggestion(List<SearchSuggestionDTO> suggestions,
                                  Set<String> seen,
                                  SearchSuggestionDTO suggestion) {
        String key = suggestion.getType() + ":" + suggestion.getSourceId();
        if (!seen.add(key)) {
            return false;
        }
        suggestions.add(suggestion);
        return suggestions.size() >= MAX_SUGGESTIONS;
    }





    private List<Shop> queryShops(String keyword, int limit) {
        return shopService.query()
                .select("id", "name", "images", "area", "address", "score", "comments")
                .likeRight("name", escapeLike(keyword))
                .orderByDesc("comments")
                .orderByDesc("id")
                .last("LIMIT " + limit)
                .list();
    }

    private List<Voucher> queryVouchers(String keyword, int limit) {
        return voucherService.query()
                .select("id", "shop_id", "title", "sub_title", "rules", "status")
                .eq("status", 1)
                .likeRight("title", escapeLike(keyword))
                .orderByDesc("id")
                .last("LIMIT " + limit)
                .list();
    }

    private List<Blog> queryBlogs(String keyword, int limit) {
        String escapedKeyword = escapeLike(keyword);
        List<Blog> titleMatches = blogService.query()
                .select("id", "shop_id", "user_id", "title", "images",
                        "content", "liked", "comments")
                .likeRight("title", escapedKeyword)
                .orderByDesc("liked")
                .orderByDesc("id")
                .last("LIMIT " + limit)
                .list();

        if (titleMatches.size() >= limit
                || keyword.codePointCount(0, keyword.length()) < MIN_NGRAM_CONTENT_LENGTH) {
            return titleMatches;
        }

        // 二字符及以上使用 ngram 全文索引，避免对 content 执行前置通配符扫描。
        List<Blog> contentMatches = blogService.query()
                .select("id", "shop_id", "user_id", "title", "images",
                        "content", "liked", "comments")
                .apply("MATCH(content) AGAINST ({0})", keyword)
                .orderByDesc("liked")
                .orderByDesc("id")
                .last("LIMIT " + limit)
                .list();

        List<Blog> merged = new ArrayList<>(limit);
        Set<Long> seen = new HashSet<>();
        appendBlogsUntilLimit(merged, seen, titleMatches, limit);
        appendBlogsUntilLimit(merged, seen, contentMatches, limit);
        return merged;
    }

    private void appendBlogsUntilLimit(List<Blog> target, Set<Long> seen,
                                       List<Blog> source, int limit) {
        for (Blog blog : source) {
            if (target.size() >= limit) {
                return;
            }
            if (seen.add(blog.getId())) {
                target.add(blog);
            }
        }
    }



    private Map<Long, Shop> loadShopMap(Collection<Voucher> vouchers,
                                        Collection<Blog> blogs) {
        Set<Long> shopIds = new HashSet<>();
        for (Voucher voucher : vouchers) {
            if (voucher.getShopId() != null) {
                shopIds.add(voucher.getShopId());
            }
        }
        for (Blog blog : blogs) {
            if (blog.getShopId() != null) {
                shopIds.add(blog.getShopId());
            }
        }
        if (shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Shop> shops = shopService.listByIds(shopIds);
        Map<Long, Shop> shopMap = new HashMap<>();
        for (Shop shop : shops) {
            shopMap.put(shop.getId(), shop);
        }
        return shopMap;
    }

    private SearchResultDTO toShopResult(Shop shop) {
        return new SearchResultDTO(
                "SHOP",
                shop.getId(),
                shop.getId(),
                shop.getName(),
                prependIfPresent("店铺", shop.getArea()),
                StrUtil.blankToDefault(shop.getAddress(), "暂无地址"),
                firstImage(shop.getImages())
        );
    }

    private SearchResultDTO toVoucherResult(Voucher voucher, Shop shop) {
        return new SearchResultDTO(
                "VOUCHER",
                voucher.getId(),
                voucher.getShopId(),
                voucher.getTitle(),
                shopName(shop) == null ? "优惠券" : "优惠券 · " + shopName(shop),
                plainText(voucher.getSubTitle(), 90),
                firstImage(shop == null ? null : shop.getImages())
        );
    }

    private SearchResultDTO toBlogResult(Blog blog, Shop shop) {
        return new SearchResultDTO(
                "BLOG",
                blog.getId(),
                blog.getShopId(),
                blog.getTitle(),
                shopName(shop) == null ? "探店笔记" : "探店笔记 · " + shopName(shop),
                plainText(blog.getContent(), 90),
                firstImage(blog.getImages())
        );
    }

    private Result validateKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Result.fail(StatusCode.INVALID_PARAM, "请输入搜索关键词");
        }
        if (keyword.codePointCount(0, keyword.length()) > MAX_KEYWORD_LENGTH) {
            return Result.fail(StatusCode.KEYWORD_TOO_LONG);
        }
        for (int i = 0; i < keyword.length(); i++) {
            if (Character.isISOControl(keyword.charAt(i))) {
                return Result.fail(StatusCode.INVALID_PARAM, "关键词包含非法字符");
            }
        }
        return null;
    }

    private boolean shouldQuery(String selectedType, String sourceType) {
        return "ALL".equals(selectedType) || sourceType.equals(selectedType);
    }

    private boolean isSupportedType(String type) {
        return "ALL".equals(type) || "SHOP".equals(type)
                || "VOUCHER".equals(type) || "BLOG".equals(type);
    }

    private String voucherSubtitle(Voucher voucher, Shop shop) {
        String shopName = shopName(shop);
        return shopName == null ? "优惠券" : "优惠券 · " + shopName;
    }

    private String shopName(Shop shop) {
        return shop == null ? null : shop.getName();
    }

    private String prependIfPresent(String prefix, String value) {
        return StrUtil.isBlank(value) ? prefix : prefix + " · " + value;
    }

    private String joinPresent(String first, String second) {
        if (StrUtil.isBlank(first)) {
            return second;
        }
        if (StrUtil.isBlank(second)) {
            return first;
        }
        return first + " · " + second;
    }

    private String firstImage(String images) {
        if (StrUtil.isBlank(images)) {
            return null;
        }
        return images.split(",")[0].trim();
    }

    private String plainText(String value, int maxLength) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String text = value.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength)) + "...";
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && prefix != null
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
