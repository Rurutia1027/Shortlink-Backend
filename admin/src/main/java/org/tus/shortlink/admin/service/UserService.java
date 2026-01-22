package org.tus.shortlink.admin.service;

import org.tus.shortlink.base.dto.req.UserLoginReqDTO;
import org.tus.shortlink.base.dto.req.UserRegisterReqDTO;
import org.tus.shortlink.base.dto.req.UserUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.UserLoginRespDTO;
import org.tus.shortlink.base.dto.resp.UserRespDTO;

/**
 * User service interface for managing users
 */
public interface UserService {

    /**
     * Get user information by username
     *
     * @param username username
     * @return user response DTO
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * Check if username exists
     *
     * @param username username
     * @return true if username exists, false otherwise
     */
    Boolean hasUsername(String username);

    /**
     * Register a new user
     *
     * @param requestParam user register request parameters
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * Update user by username
     *
     * @param requestParam user update request parameters
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * User login
     *
     * @param requestParam user login request parameters
     * @return user login response with token
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * Check if user is logged in
     *
     * @param username username
     * @param token    user login token
     * @return true if user is logged in, false otherwise
     */
    Boolean checkLogin(String username, String token);

    /**
     * User logout
     *
     * @param username username
     * @param token    user login token
     */
    void logout(String username, String token);
}
