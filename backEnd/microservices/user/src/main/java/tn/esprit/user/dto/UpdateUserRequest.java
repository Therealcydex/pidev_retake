package tn.esprit.user.dto;

import lombok.Getter;
import lombok.Setter;

import tn.esprit.user.entity.Role;

@Getter
@Setter
public class UpdateUserRequest {
    private String username;
    private String email;
    private Role role;
}
