package org.tus.shortlink.admin.service.impl;

import org.tus.shortlink.admin.service.UserService;
import org.tus.shortlink.base.dto.req.UserLoginReqDTO;
import org.tus.shortlink.base.dto.req.UserRegisterReqDTO;
import org.tus.shortlink.base.dto.req.UserUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.UserLoginRespDTO;
import org.tus.shortlink.base.dto.resp.UserRespDTO;

public class UserServiceImpl implements UserService {
    @Override
    public UserRespDTO getUserByUsername(String username) {
        return null;
    }

    @Override
    public Boolean hasUsername(String username) {
        return null;
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {

    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {

    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        return null;
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return null;
    }

    @Override
    public void logout(String username, String token) {

    }
}
