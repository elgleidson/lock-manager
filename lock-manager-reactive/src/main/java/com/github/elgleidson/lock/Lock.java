package com.github.elgleidson.lock;

import java.time.ZonedDateTime;

public record Lock(String id, String uniqueIdentifier, ZonedDateTime expiresAt) {
}
