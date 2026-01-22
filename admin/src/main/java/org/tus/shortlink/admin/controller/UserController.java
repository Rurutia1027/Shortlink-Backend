package org.tus.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.UserLoginReqDTO;
import org.tus.shortlink.base.dto.req.UserRegisterReqDTO;
import org.tus.shortlink.base.dto.req.UserUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.UserActualRespDTO;
import org.tus.shortlink.base.dto.resp.UserLoginRespDTO;
import org.tus.shortlink.base.dto.resp.UserRespDTO;
import org.tus.shortlink.admin.service.UserService;

/**
 * User controller for managing users
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/admin/v1/user")
public class UserController {
    private final UserService userService;

    /**
     * Get user information by username
     */
    @GetMapping("/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
        return Results.success(userService.getUserByUsername(username));
    }

    /**
     * Get actual user information by username (without desensitization)
     */
    @GetMapping("/actual/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username) {
        return Results.success(BeanUtil.toBean(userService.getUserByUsername(username), UserActualRespDTO.class));
    }

    /**
     * Check if username exists
     */
    @GetMapping("/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userService.hasUsername(username));
    }

    /**
     * Register a new user
     */
    @PostMapping
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam) {
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * Update user information
     */
    @PutMapping
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam) {
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * User login
     */
    @PostMapping("/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(userService.login(requestParam));
    }

    /**
     * Check if user is logged in
     */
    @GetMapping("/check-login")
    public Result<Boolean> checkLogin(@RequestParam("username") String username,
                                      @RequestParam("token") String token) {
        return Results.success(userService.checkLogin(username, token));
    }

    /**
     * User logout
     */
    @DeleteMapping("/logout")
    public Result<Void> logout(@RequestParam("username") String username,
                               @RequestParam("token") String token) {
        userService.logout(username, token);
        return Results.success();
    }
}
