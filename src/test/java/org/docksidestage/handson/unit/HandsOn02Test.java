package org.docksidestage.handson.unit;

import java.time.LocalDate;

import javax.annotation.Resource;

import org.dbflute.cbean.result.ListResultBean;
import org.docksidestage.handson.dbflute.exbhv.MemberBhv;
import org.docksidestage.handson.dbflute.exentity.Member;

public class HandsOn02Test extends UnitContainerTestCase {

    @Resource
    private MemberBhv memberBhv;

    public void test_existsTestData() {
        // ## Arrange ##

        // ## Act ##
        int actual = memberBhv.selectCount(cb -> {});

        System.out.println("actual: " + actual);

        // ## Assert ##
        assertTrue(actual > 0);
    }

    public void test_startedAtS() throws Exception {
        // ## Arrange ##
        String prefix = "S";

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().setMemberName_LikeSearch(prefix, op -> op.likePrefix());
            cb.query().addOrderBy_MemberName_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            String memberName = member.getMemberName();
            log("memberName: {}", memberName);
            assertTrue(memberName.startsWith(prefix));
        });
    }

    public void test_idIsOne() throws Exception {
        // ## Arrange ##
        // ## Act ##
        memberBhv.selectEntity(cb -> cb.acceptPK(1)).alwaysPresent(member -> {
            // ## Assert ##
            Integer memberId = member.getMemberId();
            log("memberId: {}", memberId);
            assertEquals(1, memberId);
        });
    }

    public void test_birthDayIsNull() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().setBirthdate_IsNull();
            cb.query().addOrderBy_UpdateDatetime_Desc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            LocalDate birthdate = member.getBirthdate();
            log(member.getMemberName(), birthdate);
            assertNull(birthdate);
        });
    }
}
