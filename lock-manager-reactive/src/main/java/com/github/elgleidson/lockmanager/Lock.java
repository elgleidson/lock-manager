package com.github.elgleidson.lockmanager;

import java.time.ZonedDateTime;

public record Lock(String id, String uniqueIdentifier, ZonedDateTime expiresAt) {
}
