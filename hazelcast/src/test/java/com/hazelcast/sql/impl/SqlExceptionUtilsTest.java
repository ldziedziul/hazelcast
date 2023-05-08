package com.hazelcast.sql.impl;

import com.hazelcast.jet.JetException;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SqlExceptionUtilsTest {

    @Test
    public void validIntegrityConstraintViolation_nested_SQLException() {
        SQLException sqlException = new SQLException("reason", "2300");
        JetException jetException = new JetException(sqlException);

        boolean integrityConstraintViolation = SqlExceptionUtils.isIntegrityConstraintViolation(jetException);
        assertTrue(integrityConstraintViolation);
    }

    @Test
    public void validIntegrityConstraintViolation_SQLException() {
        SQLException sqlException = new SQLException("reason", "2300");

        boolean integrityConstraintViolation = SqlExceptionUtils.isIntegrityConstraintViolation(sqlException);
        assertTrue(integrityConstraintViolation);
    }

    @Test
    public void invalidIntegrityConstraintViolation() {
        SQLException sqlException = new SQLException("reason", "2000");
        JetException jetException = new JetException(sqlException);

        boolean integrityConstraintViolation = SqlExceptionUtils.isIntegrityConstraintViolation(jetException);
        assertFalse(integrityConstraintViolation);
    }

}