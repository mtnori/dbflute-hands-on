package org.docksidestage.handson.unit;

import javax.annotation.Resource;

import org.docksidestage.handson.dbflute.exbhv.MemberBhv;

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
}
