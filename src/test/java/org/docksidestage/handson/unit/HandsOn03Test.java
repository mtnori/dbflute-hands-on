package org.docksidestage.handson.unit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.dbflute.cbean.result.ListResultBean;
import org.dbflute.cbean.result.PagingResultBean;
import org.dbflute.exception.NonSpecifiedColumnAccessException;
import org.dbflute.helper.HandyDate;
import org.docksidestage.handson.dbflute.exbhv.MemberBhv;
import org.docksidestage.handson.dbflute.exbhv.MemberSecurityBhv;
import org.docksidestage.handson.dbflute.exbhv.PurchaseBhv;
import org.docksidestage.handson.dbflute.exentity.*;

public class HandsOn03Test extends UnitContainerTestCase {

    @Resource
    MemberBhv memberBhv;

    @Resource
    MemberSecurityBhv memberSecurityBhv;

    @Resource
    PurchaseBhv purchaseBhv;

    public void test_startAtSAndLessThan19680101() throws Exception {
        // ## Arrange ##
        String targetPrefix = "S";
        LocalDate targetBirthdate = LocalDate.of(1968, 1, 1);

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().setMemberName_LikeSearch(targetPrefix, op -> op.likePrefix());
            cb.query().setBirthdate_LessEqual(targetBirthdate);
            cb.query().addOrderBy_Birthdate_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            LocalDate memberBirthdate = member.getBirthdate();
            log(member.getMemberName(), memberBirthdate);
            assertTrue(memberBirthdate.isBefore(targetBirthdate) | memberBirthdate.isEqual(targetBirthdate));
        });
    }

    public void test_memberStatusAndSecurityInfo() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            cb.setupSelect_MemberSecurityAsOne();
            cb.query().addOrderBy_Birthdate_Desc();
            cb.query().addOrderBy_MemberId_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            log(member.getMemberName(),member.getMemberStatus(),member.getMemberSecurityAsOne());
            assertTrue(member.getMemberStatus().isPresent());
            assertTrue(member.getMemberSecurityAsOne().isPresent());
        });
    }

    public void test_remind() throws Exception {
        // ## Arrange ##
        String keyword = "2";

        // ## Act ##
        // テスト都合でパフォーマンス劣化されないように、会員セキュリティ情報のデータは取得しない
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().queryMemberSecurityAsOne().setReminderQuestion_LikeSearch(keyword, opt -> opt.likeContain());
        });

        // ## Assert ##
        assertHasAnyElement(memberList);

        memberList.forEach(member -> {
            // NOTE: 複数回検索しているのがあまりよくない
            memberSecurityBhv.selectByPK(member.getMemberId()).alwaysPresent(security -> {
                String question = security.getReminderQuestion();
                log(member.getMemberName(), question);
                assertTrue(question.contains(keyword));
            });
        });

        // SQL を救出パターン
        ListResultBean<MemberSecurity> securityList = memberSecurityBhv.selectList(cb -> {
            cb.query().setMemberId_InScope(memberBhv.extractMemberIdList(memberList));
        });
        memberList.forEach(member -> {
            securityList.forEach(security -> {
                if (member.getMemberId().equals(security.getMemberId())) {
                    String question = security.getReminderQuestion();
                    log(member.getMemberName(), question);
                    assertTrue(question.contains(keyword));
                    markHere("exists");
                    // break できない！
                }
            });
            assertMarked("exists");
        });

        // stream() で探す
        memberList.forEach(member -> {
            securityList.stream().filter(security -> {
               return member.getMemberId().equals(security.getMemberId());
            }).findFirst().ifPresent(security -> {
                String question = security.getReminderQuestion();
                log(member.getMemberName(), question);
                assertTrue(question.contains(keyword));
                markHere("exists");
            });
            assertMarked("exists");
        });

        // Map にする
        Map<Integer, MemberSecurity> securityMap = securityList.stream().collect(
                Collectors.toMap(security -> security.getMemberId(), bean -> bean));
        memberList.forEach(member -> {
            MemberSecurity security = securityMap.get(member.getMemberId());
            String question = security.getReminderQuestion();
            log(member.getMemberName(), question);
            assertTrue(question.contains(keyword));
        });
    }

    public void test_4() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().queryMemberStatus().addOrderBy_DisplayOrder_Asc();
            cb.query().addOrderBy_MemberId_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);

        // Lambda 式の制約で、外部の変数を参照する場合はその変数が final である必要がある
        // 配列への参照は final のためこの制約を回避するために使用するイディオム
        String[] previousBox = new String[1];
        Set<String> statusSet = new HashSet<>();

        memberList.forEach(member -> {
            // 会員ステータスのデータが取れていないこと
            assertFalse(member.getMemberStatus().isPresent());

            String previous = previousBox[0];
            String current = member.getMemberStatusCode();

            log(previous, current);

            // ステータスが切り替わった際に判定する
            // ステータス Set に今回のステータスがまだ含まれていないこと
            if (previous != null && !previous.equals(current)) {
                assertFalse(statusSet.contains(current));
            }

            previousBox[0] = current;
            statusSet.add(current);
        });

        // 普通の For ループパターン
        // 再利用
        statusSet.clear();
        String previous = null;
        for (Member member : memberList) {
            assertFalse(member.getMemberStatus().isPresent());

            String current = member.getMemberStatusCode();
            log(previous, current);

            if (previous != null && !previous.equals(current)) {
                assertFalse(statusSet.contains(current));
            }
            statusSet.add(current);
            previous = current;
        }

        // 別のパターン
        statusSet.clear();
        previous = null;

        int switchCount = 0;
        for (Member member : memberList) {
            assertFalse(member.getMemberStatus().isPresent());
            String current = member.getMemberStatusCode();
            log(previous, current);

            // 切り替えタイミングで switchCount をインクリメントする
            if (previous != null && !previous.equals(current)) {
                ++switchCount;
            }

            statusSet.add(current);
            previous = current;
        }
        assertEquals(statusSet.size() - 1, switchCount);

    }

    public void test_5() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Purchase> purchaseList =  purchaseBhv.selectList(cb -> {
           cb.setupSelect_Member().withMemberStatus();
           cb.setupSelect_Product();
           cb.query().queryMember().setBirthdate_IsNotNull();

           cb.query().addOrderBy_PurchaseDatetime_Desc();
           cb.query().addOrderBy_PurchasePrice_Desc();
           cb.query().addOrderBy_ProductId_Asc();
           cb.query().addOrderBy_MemberId_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(purchaseList);

        purchaseList.forEach(purchase -> {
            Member member = purchase.getMember().get();
            MemberStatus status = member.getMemberStatus().get();
            Product product = purchase.getProduct().get();
            log(purchase.getProductId(), member.getMemberName(), status.getMemberStatusName(), product.getProductName(), member.getMemberId());
            assertNotNull(member.getBirthdate());
        });
    }

    public void test_6() throws Exception {
        // ## Arrange ##
        String fromDateStr = "2005/10/01";
        String toDateStr = "2005/10/03";

        LocalDateTime fromDate = new HandyDate(fromDateStr).getLocalDateTime();
        LocalDateTime toDate = new HandyDate(toDateStr).getLocalDateTime();

        String targetMemberName = "vi";

        // 10月1日ジャスト(時分秒なし)の正式会員日時を持つ会員データを作成(更新)
        adjustMember_FormalizedDatetime_FirstOnly(fromDate, targetMemberName);

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            cb.specify().specifyMemberStatus().columnMemberStatusName();
            cb.query().setMemberName_LikeSearch(targetMemberName, op -> op.likeContain());
            cb.query().setFormalizedDatetime_FromTo(fromDate, toDate, op -> op.compareAsDate());
        });

        // ## Assert ##
        memberList.forEach(member -> {
            LocalDateTime formalizedDatetime = member.getFormalizedDatetime();
            MemberStatus status = member.getMemberStatus().get();
            LocalDateTime addedToDate = toDate.plusDays(1);

            log(member.getMemberName(), formalizedDatetime, status.getMemberStatusName());

            assertNotNull(status.getMemberStatusCode());
            assertNotNull(status.getMemberStatusName());

            assertException(NonSpecifiedColumnAccessException.class, () -> status.getDisplayOrder());
            assertException(NonSpecifiedColumnAccessException.class, () -> status.getDescription());

            assertTrue(fromDate.isEqual(formalizedDatetime) || fromDate.isBefore(formalizedDatetime));
            assertTrue(addedToDate.isAfter(formalizedDatetime));
        });
    }

    public void test_7() throws Exception {
        // ## Arrange ##
        adjustPurchase_PurchaseDatetime_fromFormalizedDatetimeInWeek();

        // ## Act ##
        ListResultBean<Purchase> purchaseList = purchaseBhv.selectList(cb -> {
            cb.setupSelect_Member().withMemberStatus();
            cb.setupSelect_Member().withMemberSecurityAsOne();
            cb.setupSelect_Product().withProductStatus();
            cb.setupSelect_Product().withProductCategory().withProductCategorySelf();

            cb.columnQuery(colCB -> colCB.specify().columnPurchaseDatetime())
                    .greaterEqual(colCB -> colCB.specify().specifyMember().columnFormalizedDatetime());
            cb.columnQuery(colCB -> colCB.specify().columnPurchaseDatetime())
                    .lessThan(colCB -> colCB.specify().specifyMember().columnFormalizedDatetime())
                    .convert(op -> op.truncTime().addDay(8));
        });

        // ## Assert ##
        assertHasAnyElement(purchaseList);

        for(Purchase purchase : purchaseList) {
            Product product = purchase.getProduct().get();
            product.getProductCategory().get().getProductCategorySelf().alwaysPresent(parent -> {
                assertNotNull(parent.getProductCategoryName());
            });

            LocalDateTime purchaseDatetime = purchase.getPurchaseDatetime();
            LocalDateTime formalizedDatetime = purchase.getMember().get().getFormalizedDatetime();
            LocalDateTime oneWeekAfter = new HandyDate(formalizedDatetime).moveToDayJust().addDay(8).getLocalDateTime();

            log("purchaseDatetime={}, formalizedDatetime={}, {}", purchaseDatetime, formalizedDatetime, product.getProductName());
            assertTrue(purchaseDatetime.isEqual(formalizedDatetime) || purchaseDatetime.isAfter(formalizedDatetime));
            assertTrue(purchaseDatetime.isBefore(oneWeekAfter));
        }
    }

    public void test_8() throws Exception {
        // ## Arrange ##
        String targetDateStr = "1974/01/01";
        LocalDate targetDate = new HandyDate(targetDateStr).getLocalDate();

        LocalDate limitDate = adjustExercise8_Birthdate_asLimitDate(targetDate);
        LocalDate overDate = adjustExercise8_Birthdate_asOverDate(targetDate);

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            cb.setupSelect_MemberSecurityAsOne();
            cb.setupSelect_MemberWithdrawalAsOne();
            cb.query().setBirthdate_FromTo(null, targetDate, op -> op.compareAsYear().allowOneSide().orIsNull());
            cb.query().addOrderBy_Birthdate_Desc().withNullsFirst();;
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        boolean existsLimitDate = false;
        for (Member member : memberList) {
            MemberStatus status = member.getMemberStatus().get();
            MemberSecurity security = member.getMemberSecurityAsOne().get();
            String reason = member.getMemberWithdrawalAsOne().map(wdl -> wdl.getWithdrawalReasonInputText()).orElse("none");
            log(status.getMemberStatusName(), security.getReminderQuestion(), security.getReminderAnswer(), reason);

            LocalDate birthdate = member.getBirthdate();
            if (birthdate != null) {
                assertTrue(birthdate.isBefore(overDate));
                if (birthdate.isEqual(limitDate)) {
                    existsLimitDate = true;
                }
            }
        }
        assertTrue(existsLimitDate);
        assertNull(memberList.get(0).getBirthdate()); // 先頭なのでこれでOK
    }

    private LocalDate adjustExercise8_Birthdate_asLimitDate(LocalDate targetDate) {
        LocalDate limitDate = new HandyDate(targetDate).moveToYearTerminal().getLocalDate();
        Member member = new Member();
        member.setMemberId(3);
        member.setBirthdate(limitDate);
        memberBhv.updateNonstrict(member);
        return limitDate;
    }

    private LocalDate adjustExercise8_Birthdate_asOverDate(LocalDate targetDate) {
        LocalDate overDate = targetDate.plusYears(1);
        Member member = new Member();
        member.setMemberId(5);
        member.setBirthdate(overDate);
        memberBhv.updateNonstrict(member);
        return overDate;
    }

    public void test_9() throws Exception {
        // ## Arrange ##
        String targetDateStr = "2005/06/01";
        LocalDate targetDate = new HandyDate(targetDateStr).getLocalDate();

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            // 生年月日がないこと
            cb.query().setBirthdate_IsNull();
            // 正式会員になった日付で照準ソートし、さらに 2005/06 に正式会員になった会員を先に並べる
            cb.query().addOrderBy_FormalizedDatetime_Asc().withManualOrder(op -> {
                op.when_FromTo(targetDate, targetDate, ftOp -> ftOp.compareAsMonth());
            });
            // 第二ソートキーとして、会員IDの降順
            cb.query().addOrderBy_MemberId_Desc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);

        // 対象年月が存在すること
        boolean existsTargetMonth = false;
        // 対象年月の境界を超えたかどうか
        boolean passedBorder = false;

        HandyDate fromHandy = new HandyDate(targetDate);
        for (Member member : memberList) {
            log(member.getFormalizedDatetime());

            // 誕生日がNullであること
            assertNull(member.getBirthdate());

            // 2025/06 に正式会員になった会員が先に並んでいること
            LocalDateTime formalizedDateTime = member.getFormalizedDatetime();

            // 対象年月の塊を2回通った、あるいは対象年月が先頭でない場合、その後の繰り返しで assertFalse のロジックを通り、失敗するため検知できる
            if (formalizedDateTime != null && fromHandy.isMonthOfYearSameAs(formalizedDateTime)) {
                // 対象年月の境界を超えていないことをチェック
                assertFalse(passedBorder);
                // 対象年月のデータが存在していることを確認できたのでtrueにする
                existsTargetMonth = true;
            } else {
                // 対象年月でなければtrueにする
                passedBorder = true;
            }
        }

        assertTrue(existsTargetMonth);
        assertTrue(passedBorder);
    }

    public void test_paging() throws Exception {
        // ## Arrange ##

        // ## Act ##
        PagingResultBean<Member> page = memberBhv.selectPage(cb -> {
            cb.setupSelect_MemberStatus();
            cb.specify().specifyMemberStatus().columnMemberStatusName();
            cb.query().addOrderBy_MemberId_Asc();
            cb.paging(3,1);
        });

        // ## Assert ##
        assertHasAnyElement(page);
        page.forEach(member -> {
            log(member.getMemberId(),member.getMemberName(),member.getMemberStatus().get().getMemberStatusName());
        });
        int allRecordCount = page.getAllRecordCount();
        assertEquals(memberBhv.selectCount(cb -> {}), allRecordCount);
        assertEquals((allRecordCount / 3) + (allRecordCount % 3 > 0 ? 1 : 0), page.getAllPageCount());
        assertEquals(3, page.getPageSize());
        assertEquals(1, page.getCurrentPageNumber());
        assertEquals(3, page.size());
        // 前後3ページ分のリストを表示をアサート
        assertEquals(Arrays.asList(1, 2, 3, 4), page.pageRange(op -> op.rangeSize(3)).createPageNumberList());
        // 前のページがないことをアサート
        assertFalse(page.existsPreviousPage());
        // 後ろのページがあることをアサート
        assertTrue(page.existsNextPage());
    }

    public void test_cursor() throws Exception {
        // ## Arrange ##

        String[] previousBox = new String[1];
        Set<String> statusSet = new HashSet<>();

        // ## Act ##
        memberBhv.selectCursor(cb -> {
            cb.setupSelect_MemberStatus();
            cb.query().queryMemberStatus().addOrderBy_DisplayOrder_Asc();
            cb.query().addOrderBy_MemberId_Asc();
        }, member -> {
            // ## Assert ##

            // 会員ステータスが取れていることをアサート
            assertTrue(member.getMemberStatus().isPresent());

            // 会員ステータスごとに固まって並んでいることをアサート
            String previous = previousBox[0];
            String current = member.getMemberStatusCode();
            log(previous, current);

            // 前回のステータスコードと異なる場合に、現在のステータスコードがすでにSetに入っていないことをアサートする
            if (previous != null && !previous.equals(current)) {
                assertFalse(statusSet.contains(current));
            }

            // 現在のデータを配列に一時保持
            previousBox[0] = current;
            // 現在のステータスをSetにセット
            statusSet.add(current);
        });
        assertHasAnyElement(statusSet);
    }

    // InnerJoinAutoDetectは、手動の (Manual)InnerJoin とは別に、InnerJoin 可能な結合、
    // つまり外部結合である必要のない外部結合を自動判別する機能
    // @see https://dbflute.seasar.org/ja/data/model/maihamadb-erd.png
    public void test_confirm_InnerJoinAutoDetect() throws Exception {
        // ## Arrange ##

        // ## Act ##
        memberBhv.selectList(cb -> {
            // Not NullのカラムでFK制約のあるリレーションを内部結合にする
            // 相手側のデータは必ず存在する
            cb.setupSelect_MemberStatus();

            // 通常は外部結合
            cb.setupSelect_MemberSecurityAsOne();

            // Where 句で絞り込み条件として利用されているリレーションを内部結合にする
            // (一部例外を除き) 相手側のデータは必ず存在する
            cb.setupSelect_MemberWithdrawalAsOne();
            cb.query().queryMemberWithdrawalAsOne().setWithdrawalDatetime_GreaterEqual(currentLocalDateTime());
        });

        // ## Assert ##
    }
}
