package com.matriz.identity;

import java.util.UUID;

/** Public contact data of a user ({@code UserContact} in openapi.yaml). */
public record UserContact(UUID id, String name, String email) {
}
