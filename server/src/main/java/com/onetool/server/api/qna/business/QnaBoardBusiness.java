package com.onetool.server.api.qna.business;

import com.onetool.server.api.member.domain.Member;
import com.onetool.server.api.member.service.MemberService;
import com.onetool.server.api.qna.QnaBoard;
import com.onetool.server.api.qna.dto.request.PostQnaBoardRequest;
import com.onetool.server.api.qna.dto.response.QnaBoardBriefResponse;
import com.onetool.server.api.qna.dto.response.QnaBoardDetailResponse;
import com.onetool.server.api.qna.service.QnaBoardService;
import com.onetool.server.global.annotation.Business;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.List;

@Business
@RequiredArgsConstructor
public class QnaBoardBusiness {

    private final QnaBoardService qnaBoardService;
    private final MemberService memberService;


    public List<QnaBoardBriefResponse> getQnaBoardBriefList() {

        List<QnaBoard> qnaBoards = qnaBoardService.findAllQnaBoards();
        return QnaBoardBriefResponse.fromQnaBoardListToBriefResponseList(qnaBoards);
    }

    @Transactional
    public void createQnaBoard(Principal principal, PostQnaBoardRequest request) {

        Member member = memberService.findMember(principal.getName());
        QnaBoard qnaBoard = request.toQnaBoard();
        qnaBoardService.saveQnaBoard(member, qnaBoard);
    }

    @Transactional
    public QnaBoardDetailResponse getQnaBoardDetail(Principal principal, Long qnaId) {

        Member member = memberService.findMember(principal.getName());
        QnaBoard qnaBoard = qnaBoardService.findQnaBoardById(qnaId);
        boolean authorization = qnaBoard.isMyQnaBoard(member);

        return QnaBoardDetailResponse.fromQnaBoardToDetailResponse(qnaBoard, authorization);
    }

    @Transactional
    public void removeQnaBoard(Principal principal, Long qnaId) {

        Member member = memberService.findMember(principal.getName());
        QnaBoard qnaBoard = qnaBoardService.findQnaBoardById(qnaId);
        qnaBoard.validateMemberCanRemoveOrUpdate(member);

        qnaBoardService.deleteQnaBoard(qnaBoard, member);
    }

    @Transactional
    public void editQnaBoard(Principal principal, Long qnaId, PostQnaBoardRequest request) {

        Member member = memberService.findMember(principal.getName());
        QnaBoard qnaBoard = qnaBoardService.findQnaBoardById(qnaId);
        qnaBoard.validateMemberCanRemoveOrUpdate(member);

        qnaBoardService.updateQnaBoard(qnaBoard, request);
    }

}
