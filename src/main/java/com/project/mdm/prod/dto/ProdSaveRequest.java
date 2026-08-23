package com.project.mdm.prod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 신규 행의 상품 코드는 클라이언트에서 받지 않는다 — 서버가 시퀀스로 채번한다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(채번 · 삭제 참조 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProdSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long prodId;
    private String prodNm;
    private TmpZon tmpZon;
    /** 발주·납품 단위 (공통코드 UOM). 신규 등록 시 그 포장이 없으면 낱개수량 1로 자동 생성된다. 등록 후 변경은 단위 관리 화면 */
    private String inbUomCd;
    /** 출고주문 단위 (공통코드 UOM). 마찬가지로 신규 등록 시에만 자동 생성되고 등록 후엔 못 바꾼다 */
    private String outbUomCd;
    private Integer shelfLifeDays;
    /** 상품 이미지 URL. 기본은 프론트와 함께 배포되는 정적 파일의 루트 상대경로({@code /prod-img/{상품코드}.svg}) */
    private String imgUrl;

    /** 신규 행 → 엔티티. 상품 코드는 서비스가 채번해 넘긴다. 단위 필수 검사는 신규에만 있다 */
    public Prod toEntity(String prodCd) {
        validateFields();
        requireUomCd(inbUomCd, "입고단위");
        requireUomCd(outbUomCd, "출고단위");
        Prod prod = Prod.builder()
                .prodCd(prodCd)
                .prodNm(prodNm)
                .tmpZon(tmpZon)
                .inbUomCd(inbUomCd)
                .outbUomCd(outbUomCd)
                .shelfLifeDays(shelfLifeDays)
                .imgUrl(imgUrl)
                .build();
        prod.ensureRoleUoms();
        return prod;
    }

    /** 수정 행 → 기존 엔티티에 반영. 입고/출고단위는 보지 않는다 — 등록 후 변경은 단위 관리 화면이 맡는다 */
    public void updateEntity(Prod prod) {
        validateFields();
        prod.update(prodNm, tmpZon, shelfLifeDays, imgUrl);
    }

    private void validateFields() {
        if (prodNm == null || prodNm.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (tmpZon == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + prodNm);
        }
        // NULL = 유통기한 미관리(공산품 등). 값이 있으면 1 이상이어야 한다.
        if (shelfLifeDays != null && shelfLifeDays < 1) {
            throw new IllegalArgumentException("유통기한(일)은 비워두거나(미관리) 1 이상이어야 합니다: " + prodNm);
        }
        validateImgUrl();
    }

    /**
     * 이미지 URL은 자기 필드만으로 되는 검사(형식 · 길이)만 여기서 본다 — 그 주소에 파일이 실제로
     * 있는지는 백엔드가 알 수 없다(프론트가 배포한 정적 파일이다).
     * 그리드에서 값을 지우면 빈 문자열이 오므로 NULL(이미지 없음)로 맞춰 둔다 —
     * 그래야 컬럼이 ''와 NULL 두 벌로 갈리지 않는다.
     * <p>
     * 세 가지 형태를 받는다 —
     * <ul>
     *   <li>{@code emoji:🥛} — 이모지(<b>화면이 넣는 유일한 형태</b>). 상품 관리 화면이 목록에서
     *       고르게 한다. 글자라 파일도 스토리지도 아이콘 라이브러리도 없고, 라이선스 의무나
     *       「없는 아이콘 이름」 같은 실패가 아예 생기지 않는다.</li>
     *   <li>{@code /prod-img/PROD-0001.svg} — 시더 상품에 붙은 전용 그림(정적 파일).
     *       화면에서 넣을 수는 없고 시드로만 들어온다 — 파일을 미리 소스 폴더에 넣어 두는
     *       화면 밖 단계를 전제하는 방식이라 사용자 기능에서 뺐다.</li>
     *   <li>{@code https://…} — 이미 어딘가에 떠 있는 이미지의 절대 주소. 업로드 기능은 없고
     *       (Supabase Storage 업로드 경로를 옵션으로 뒀다가 뺐다 — 켜지 않는 옵션은 남기지 않는다),
     *       손으로 적는 절대 주소까지 막을 이유는 없어 형식만 허용해 둔다.</li>
     * </ul>
     * 이모지가 화면의 목록에 실제로 있는지는 검사하지 않는다 — 목록의 주인이 프론트 상수라
     * 서버가 알 수 없고, 없는 값이면 화면이 「이미지 없음」 폴백으로 흡수한다(파일 경로와 같은 성격).
     * <p>
     * {@code http://}는 받지 않는다 — 배포가 https라 혼합 콘텐츠로 차단되어 그림이 안 뜬다.
     */
    private void validateImgUrl() {
        if (imgUrl != null && imgUrl.isBlank()) {
            imgUrl = null;
            return;
        }
        if (imgUrl == null) {
            return;
        }
        if (imgUrl.length() > 500) {
            throw new IllegalArgumentException("이미지 URL이 너무 깁니다(최대 500자): " + prodNm);
        }
        if (!imgUrl.startsWith("emoji:") && !imgUrl.startsWith("/") && !imgUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "이미지는 아이콘(emoji:…)이거나 /로 시작하는 경로, https:// 주소여야 합니다: " + prodNm);
        }
    }

    /** 빈 값만 막는다 — 공통코드 UOM에 실재하는지는 화면 콤보박스가 보장한다 */
    private void requireUomCd(String uomCd, String label) {
        if (uomCd == null || uomCd.isBlank()) {
            throw new IllegalArgumentException(label + "는 필수입니다: " + prodNm);
        }
    }
}
