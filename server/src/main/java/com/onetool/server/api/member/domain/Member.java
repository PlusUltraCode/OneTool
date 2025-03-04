package com.onetool.server.api.member.domain;

import com.onetool.server.api.cart.Cart;
import com.onetool.server.api.member.enums.SocialType;
import com.onetool.server.api.member.enums.UserRole;
import com.onetool.server.global.entity.BaseEntity;
import com.onetool.server.api.member.dto.MemberUpdateRequest;
import com.onetool.server.api.order.Orders;
import com.onetool.server.api.qna.QnaBoard;
import com.onetool.server.api.qna.QnaReply;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Slf4j
@SQLDelete(sql = "UPDATE Member SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String password;

    @NotNull
    @Size(min = 1, max = 100, message = "이메일은 1 ~ 100자 이여야 합니다.")
    @Email
    @Column(unique = true)
    private String email;

    @NotNull(message = "이름은 null 일 수 없습니다.")
    @Size(min = 1, max = 10, message = "이름은 1 ~ 10자 이여야 합니다.")
    private String name;

    @Column(name = "birth_date")
    @Past
    private LocalDate birthDate;

    @Column(name = "phone_num")
    @Size(min = 10, max = 11)
    private String phoneNum;

    @Column(name = "user_role")
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @ColumnDefault("'기타'")
    private String field;

    @Column(name = "is_native")
    private boolean isNative;

    @Column(name = "service_accept")
    private boolean serviceAccept;

    @Column(name = "platform_type")
    private String platformType;

    @Column(name = "social_type")
    private SocialType socialType;

    private String socialId;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL)
    private List<QnaBoard> qnaBoards = new ArrayList<>();

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL)
    private List<QnaReply> qnaReplies = new ArrayList<>();

    @OneToOne(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Cart cart;

    @OneToMany(mappedBy = "member")
    @OrderBy("createdAt DESC")
    private List<Orders> orders = new ArrayList<>();

    @Column(name = "user_registered_at")
    private LocalDate user_registered_at;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Builder
    public Member(Long id, String password, String email, String name, LocalDate birthDate, String phoneNum, UserRole role, String field, boolean isNative, boolean serviceAccept, String platformType, SocialType socialType, String socialId, List<QnaBoard> qnaBoards, List<QnaReply> qnaReplies, Cart cart, boolean isDeleted) {
        this.id = id;
        this.password = password;
        this.email = email;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNum = phoneNum;
        this.role = role;
        this.field = field;
        this.isNative = isNative;
        this.serviceAccept = serviceAccept;
        this.platformType = platformType;
        this.socialType = socialType;
        this.socialId = socialId;
        this.qnaBoards = qnaBoards;
        this.qnaReplies = qnaReplies;
        this.cart = cart != null ? cart : Cart.createCart(this);
        this.isDeleted = isDeleted;
    }

    @Transactional
    public void updateWith(MemberUpdateRequest request, PasswordEncoder encoder) {
        Optional.ofNullable(request.getName()).ifPresent(this::setName);
        Optional.ofNullable(request.getPhoneNum()).ifPresent(this::setPhoneNum);
        Optional.ofNullable(request.getDevelopmentField()).ifPresent(this::setField);
        Optional.ofNullable(request.getNewPassword()).ifPresent(newPassword -> {
            log.info("new password: {}", newPassword);
            this.setPassword(encoder.encode(newPassword));
        });
    }

    public boolean getIsDeleted() {
        return isDeleted;
    }
}