package com.onetool.server.api.member.service;

import com.onetool.server.api.blueprint.Blueprint;
import com.onetool.server.api.member.dto.*;
import com.onetool.server.api.qna.dto.response.QnaBoardResponse;
import com.onetool.server.api.qna.repository.QnaBoardRepository;
import com.onetool.server.global.auth.MemberAuthContext;
import com.onetool.server.global.auth.jwt.JwtUtil;
import com.onetool.server.global.exception.*;
import com.onetool.server.global.exception.base.BaseException;
import com.onetool.server.global.exception.codes.ErrorCode;
import com.onetool.server.global.redis.service.MailRedisService;
import com.onetool.server.api.mail.MailService;

import com.onetool.server.api.member.repository.MemberRepository;
import com.onetool.server.api.member.domain.Member;
import com.onetool.server.api.order.OrderBlueprint;
import com.onetool.server.api.qna.QnaBoard;
import com.onetool.server.global.redis.service.TokenBlackListRedisService;
import com.onetool.server.global.redis.service.TokenRedisService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.integration.IntegrationProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static com.onetool.server.global.exception.codes.ErrorCode.NON_EXIST_USER;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private static final String AUTH_CODE_PREFIX = "AuthCode ";

    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;
    private final PasswordEncoder encoder;

    private final MailService mailService;
    private final MailRedisService mailRedisService;

    private final TokenRedisService tokenRedisService;

    private final TokenBlackListRedisService tokenBlackListRedisService;

    private final QnaBoardRepository qnaBoardRepository;

    @Value("${spring.mail.auth-code-expiration-millis}")
    private long authCodeExpirationMillis;

    public MemberCreateResponse createMember(MemberCreateRequest request) {
        boolean isExist = memberRepository.existsByEmail(request.email());
        if (isExist) {
            throw new BaseException(ErrorCode.EXIST_EMAIL);
        }

        Member member = memberRepository.save(request.toEntity(encoder.encode(request.password())));
        log.info("회원가입됨:" + member.getEmail());
        return MemberCreateResponse.of(member);
    }

    public Map<String, String> login(LoginRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new MemberNotFoundException(email));
        log.info("============== 로그인 유저 정보 ===============");
        log.info(member.toString());

        if (!encoder.matches(password, member.getPassword())) {
            log.error("비밀번호 불일치: " + encoder.encode(member.getPassword()));
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        MemberAuthContext context = MemberAuthContext.builder()
                .id(member.getId())
                .role(member.getRole().name())
                .name(member.getName())
                .email(member.getEmail())
                .password(member.getPassword())
                .build();

        return jwtUtil.createTokens(context);
    }

    public String findEmail(MemberFindEmailRequest request) {
        String name = request.name();
        String phoneNum = request.phone_num();

        Member member = memberRepository.findByNameAndPhoneNum(name, phoneNum).orElseThrow(() -> new MemberNotFoundException(name));

        return member.getEmail();
    }

    public void sendCodeToEmail(String toEmail) {
        //this.checkDuplicatedEmail(toEmail);
        String title = "[원툴] 회원가입 이메일 인증번호";
        String authCode = this.createCode();
        mailService.sendEmail(toEmail, title, authCode, true);
        // 이메일 인증 요청 시 인증 번호 Redis에 저장 ( key = "AuthCode " + Email / value = AuthCode )
        mailRedisService.setValues(AUTH_CODE_PREFIX + toEmail,
                authCode, Duration.ofMillis(this.authCodeExpirationMillis));
    }

    private void checkDuplicatedEmail(String email) {
        Optional<Member> member = memberRepository.findByEmail(email);
        if (member.isPresent()) {
            log.debug("MemberServiceImpl.checkDuplicatedEmail exception occur email: {}", email);
            throw new DuplicateMemberException(email);
        }
    }

    private String createCode() {
        int length = 6;
        try {
            Random random = SecureRandom.getInstanceStrong();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                builder.append(random.nextInt(10));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            log.debug("MemberService.createCode() exception occur");
            throw new BusinessLogicException("잘못된 생성알고리즘", ErrorCode.AUTH_CODE_ERROR);
        }
    }

    public boolean verifiedCode(String email, String authCode) {
        //this.checkDuplicatedEmail(email);
        String redisAuthCode = mailRedisService.getValues(AUTH_CODE_PREFIX + email);
        return mailRedisService.checkExistsValue(redisAuthCode) && redisAuthCode.equals(authCode);
    }

    public boolean findLostPwd(MemberFindPwdRequest request) {
        String email = request.getEmail();
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new MemberNotFoundException(email));

        String newPwd = createRandomPassword();
        member.setPassword(encoder.encode(newPwd));
        memberRepository.save(member);

        mailService.sendEmail(
                member.getEmail(),
                "[원툴] 임시 비밀번호 발급",
                newPwd,
                false
        );
        return true;
    }

    @Transactional
    public void updateMember(Long id, MemberUpdateRequest request) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new MemberNotFoundException(id.toString()));
        member.updateWith(request, encoder);
        memberRepository.save(member);
    }

    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepository.findById(id).orElseThrow(() -> new MemberNotFoundException(id.toString()));
        memberRepository.delete(member);
    }

    private String createRandomPassword() {
        int length = 15;
        try {
            Random random = SecureRandom.getInstanceStrong();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                builder.append(random.nextInt(10));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            log.debug("MemberService.createRandomPassword() exception occur");
            throw new BusinessLogicException("생성 알고리즘 문제", ErrorCode.RANDOM_PASSWORD_ERROR);
        }
    }

    public MemberInfoResponse getMemberInfo(Long userId) {
        Member member = memberRepository.findByIdAndIsDeleted(userId, false).orElseThrow(() -> new MemberNotFoundException(userId.toString()));

        return MemberInfoResponse.from(member);
    }

    public List<QnaBoardResponse.QnaBoardBriefResponse> findQnaWrittenById(MemberAuthContext context) {
        if (!findMemberWithQna(context.getId())) {
            throw new BaseException(ErrorCode.NON_EXIST_USER);
        }
        List<QnaBoard> qnaBoards = qnaBoardRepository.findByMemberId(context.getId());
        return QnaBoardResponse.QnaBoardBriefResponse.from(qnaBoards);
    }

    private boolean findMemberWithQna(Long id) {
        return memberRepository.existsById(id);
    }

    public List<BlueprintDownloadResponse> getPurchasedBlueprints(final Long userId) {
        final Member member = memberRepository.findById(userId).orElseThrow(() -> new MemberNotFoundException(userId.toString()));

        return member.getOrders().stream()
                .flatMap(order -> order.getOrderItems().stream())
                .map(this::convertToBlueprintDownloadResponse)
                .collect(Collectors.toList());
    }

    private BlueprintDownloadResponse convertToBlueprintDownloadResponse(final OrderBlueprint orderItem) {
        final Blueprint blueprint = orderItem.getBlueprint();
        return new BlueprintDownloadResponse(
                blueprint.getId(),
                blueprint.getBlueprintImg(),
                blueprint.getDownloadLink(),
                blueprint.getBlueprintName(),
                blueprint.getCreatorName()
        );
    }

    public ApiResponse<String> logout(String accessToken, String email) {
        Long expiration = jwtUtil.getExpirationMilliSec(accessToken);
        tokenBlackListRedisService.setBlackList(accessToken, expiration);
        if (tokenRedisService.hasKey(email)) {
            tokenRedisService.deleteValues(email);
        } else {
            throw new IllegalLogoutMember(ErrorCode.ILLEGAL_LOGOUT_USER);
        }
        return ApiResponse.onSuccess("로그아웃이 완료되었습니다.");
    }

    public Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(NON_EXIST_USER));
    }
}