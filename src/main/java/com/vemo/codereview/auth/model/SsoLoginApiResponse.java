package com.vemo.codereview.auth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SsoLoginApiResponse {

    private String code;
    private TokenData data;
    private String msg;

    @Getter
    @Setter
    public static class TokenData {

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private Long expiresIn;

        @JsonProperty("refresh_token")
        private String refreshToken;
    }
}
