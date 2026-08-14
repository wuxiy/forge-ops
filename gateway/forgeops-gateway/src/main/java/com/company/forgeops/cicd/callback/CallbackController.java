package com.company.forgeops.cicd.callback;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Git / CI / Deployment / Multica 回调入口（§9.3 / §16）。 */
@RestController
@RequestMapping("/api/v1/callback")
public class CallbackController {

    private final CallbackService callbackService;
    private final String callbackSecret;

    public CallbackController(
            CallbackService callbackService,
            @Value("${forgeops.callback.secret}") String callbackSecret) {
        this.callbackService = callbackService;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/{source}")
    public Map<String, String> callback(
            @PathVariable String source,
            @RequestHeader(value = "X-ForgeOps-Callback-Secret", required = false) String secret,
            @RequestBody CallbackService.CallbackPayload payload) {
        if (callbackSecret != null && !callbackSecret.isBlank() && !callbackSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "回调 secret 校验失败");
        }
        String result = callbackService.handle(source.toUpperCase(), payload);
        return Map.of("result", result);
    }
}
