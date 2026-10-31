package com.dzdp.interceptor;

import cn.hutool.json.JSONUtil;
import com.dzdp.dto.Result;
import com.dzdp.dto.StatusCode;
import com.dzdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

//鉴权拦截器
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       //1.判断是否需要拦截（ThreadLocal）中是否有用户
        if(UserHolder.getUser()==null){
            //没有需要拦截，设置状态码，并保持统一的 Result 响应结构
            response.setStatus(401);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    JSONUtil.toJsonStr(Result.fail(StatusCode.UNAUTHORIZED))
            );
            //拦截
            return false;
        }
        //由用户放行
        return true;
    }
}
