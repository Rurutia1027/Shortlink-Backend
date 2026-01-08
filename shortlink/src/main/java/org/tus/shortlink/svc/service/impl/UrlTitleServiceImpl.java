package org.tus.shortlink.svc.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.shortlink.svc.service.UrlTitleService;

@RequiredArgsConstructor
@Service
public class UrlTitleServiceImpl implements UrlTitleService {
    @Override
    public String getTitleByUrl(String url) {
        // TODO
        return "";
    }
}
