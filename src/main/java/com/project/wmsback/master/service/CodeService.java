package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.CodeResponse;
import com.project.wmsback.master.repository.CodeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeDetailRepository codeDetailRepository;

    /** 그룹의 사용중(Y) 코드 목록 (srt_seq 순) */
    public List<CodeResponse> list(String grpCd) {
        return codeDetailRepository.findByGrpCdAndUsYnOrderBySrtSeq(grpCd, 'Y').stream()
                .map(CodeResponse::from)
                .toList();
    }
}