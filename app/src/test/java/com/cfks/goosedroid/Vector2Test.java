package com.cfks.goosedroid;

import com.cfks.goosedroid.SamEngine.Vector2;

import org.junit.Test;

import static org.junit.Assert.*;

public class Vector2Test {

    private static final float EPSILON = 0.001f;

    // ============== CONSTRUCTOR ==============

    @Test
    public void constructor_setsXY() {
        Vector2 v = new Vector2(3f, 4f);
        assertEquals(3f, v.x, EPSILON);
        assertEquals(4f, v.y, EPSILON);
    }

    @Test
    public void zero_isOrigin() {
        assertEquals(0f, Vector2.zero.x, EPSILON);
        assertEquals(0f, Vector2.zero.y, EPSILON);
    }

    // ============== ADDITION ==============

    @Test
    public void add_sumsComponents() {
        Vector2 a = new Vector2(1f, 2f);
        Vector2 b = new Vector2(3f, 4f);
        Vector2 result = Vector2.add(a, b);
        assertEquals(4f, result.x, EPSILON);
        assertEquals(6f, result.y, EPSILON);
    }

    @Test
    public void add_withZero() {
        Vector2 a = new Vector2(5f, 7f);
        Vector2 result = Vector2.add(a, Vector2.zero);
        assertEquals(5f, result.x, EPSILON);
        assertEquals(7f, result.y, EPSILON);
    }

    @Test
    public void add_withNegative() {
        Vector2 a = new Vector2(5f, 3f);
        Vector2 b = new Vector2(-2f, -1f);
        Vector2 result = Vector2.add(a, b);
        assertEquals(3f, result.x, EPSILON);
        assertEquals(2f, result.y, EPSILON);
    }

    // ============== SUBTRACTION ==============

    @Test
    public void subtract_diffComponents() {
        Vector2 a = new Vector2(5f, 7f);
        Vector2 b = new Vector2(2f, 3f);
        Vector2 result = Vector2.subtract(a, b);
        assertEquals(3f, result.x, EPSILON);
        assertEquals(4f, result.y, EPSILON);
    }

    @Test
    public void subtract_fromSelf_isZero() {
        Vector2 a = new Vector2(5f, 7f);
        Vector2 result = Vector2.subtract(a, a);
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    // ============== NEGATION ==============

    @Test
    public void negate_flipsSign() {
        Vector2 a = new Vector2(3f, -4f);
        Vector2 result = Vector2.negate(a);
        assertEquals(-3f, result.x, EPSILON);
        assertEquals(4f, result.y, EPSILON);
    }

    // ============== MULTIPLICATION ==============

    @Test
    public void multiply_vectorByVector() {
        Vector2 a = new Vector2(2f, 3f);
        Vector2 b = new Vector2(4f, 5f);
        Vector2 result = Vector2.multiply(a, b);
        assertEquals(8f, result.x, EPSILON);
        assertEquals(15f, result.y, EPSILON);
    }

    @Test
    public void multiply_vectorByScalar() {
        Vector2 a = new Vector2(3f, 4f);
        Vector2 result = Vector2.multiply(a, 2f);
        assertEquals(6f, result.x, EPSILON);
        assertEquals(8f, result.y, EPSILON);
    }

    @Test
    public void multiply_byZero() {
        Vector2 a = new Vector2(3f, 4f);
        Vector2 result = Vector2.multiply(a, 0f);
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    @Test
    public void multiply_byNegative() {
        Vector2 a = new Vector2(3f, 4f);
        Vector2 result = Vector2.multiply(a, -1f);
        assertEquals(-3f, result.x, EPSILON);
        assertEquals(-4f, result.y, EPSILON);
    }

    // ============== DIVISION ==============

    @Test
    public void divide_byScalar() {
        Vector2 a = new Vector2(6f, 8f);
        Vector2 result = Vector2.divide(a, 2f);
        assertEquals(3f, result.x, EPSILON);
        assertEquals(4f, result.y, EPSILON);
    }

    // ============== DISTANCE ==============

    @Test
    public void distance_between_345_triangle() {
        Vector2 a = new Vector2(0f, 0f);
        Vector2 b = new Vector2(3f, 4f);
        assertEquals(5f, Vector2.Distance(a, b), EPSILON);
    }

    @Test
    public void distance_toSelf_isZero() {
        Vector2 a = new Vector2(5f, 3f);
        assertEquals(0f, Vector2.Distance(a, a), EPSILON);
    }

    @Test
    public void distance_isCommutative() {
        Vector2 a = new Vector2(1f, 2f);
        Vector2 b = new Vector2(4f, 6f);
        assertEquals(Vector2.Distance(a, b), Vector2.Distance(b, a), EPSILON);
    }

    // ============== MAGNITUDE ==============

    @Test
    public void magnitude_345() {
        Vector2 v = new Vector2(3f, 4f);
        assertEquals(5f, Vector2.Magnitude(v), EPSILON);
    }

    @Test
    public void magnitude_zero() {
        assertEquals(0f, Vector2.Magnitude(Vector2.zero), EPSILON);
    }

    @Test
    public void magnitude_unitX() {
        Vector2 v = new Vector2(1f, 0f);
        assertEquals(1f, Vector2.Magnitude(v), EPSILON);
    }

    // ============== NORMALIZE ==============

    @Test
    public void normalize_unitLength() {
        Vector2 v = new Vector2(3f, 4f);
        Vector2 normalized = Vector2.Normalize(v);
        assertEquals(1f, Vector2.Magnitude(normalized), EPSILON);
    }

    @Test
    public void normalize_preservesDirection() {
        Vector2 v = new Vector2(3f, 4f);
        Vector2 normalized = Vector2.Normalize(v);
        assertEquals(0.6f, normalized.x, EPSILON);
        assertEquals(0.8f, normalized.y, EPSILON);
    }

    @Test
    public void normalize_zeroVector_returnsZero() {
        Vector2 result = Vector2.Normalize(new Vector2(0f, 0f));
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    // ============== DOT PRODUCT ==============

    @Test
    public void dot_perpendicular_isZero() {
        Vector2 a = new Vector2(1f, 0f);
        Vector2 b = new Vector2(0f, 1f);
        assertEquals(0f, Vector2.Dot(a, b), EPSILON);
    }

    @Test
    public void dot_parallel_isProduct() {
        Vector2 a = new Vector2(2f, 0f);
        Vector2 b = new Vector2(3f, 0f);
        assertEquals(6f, Vector2.Dot(a, b), EPSILON);
    }

    @Test
    public void dot_opposite_isNegative() {
        Vector2 a = new Vector2(1f, 0f);
        Vector2 b = new Vector2(-1f, 0f);
        assertTrue(Vector2.Dot(a, b) < 0);
    }

    @Test
    public void dot_withSelf_isMagnitudeSquared() {
        Vector2 v = new Vector2(3f, 4f);
        float mag = Vector2.Magnitude(v);
        assertEquals(mag * mag, Vector2.Dot(v, v), EPSILON);
    }

    // ============== LERP ==============

    @Test
    public void lerp_atZero_returnsA() {
        Vector2 a = new Vector2(0f, 0f);
        Vector2 b = new Vector2(10f, 20f);
        Vector2 result = Vector2.Lerp(a, b, 0f);
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }

    @Test
    public void lerp_atOne_returnsB() {
        Vector2 a = new Vector2(0f, 0f);
        Vector2 b = new Vector2(10f, 20f);
        Vector2 result = Vector2.Lerp(a, b, 1f);
        assertEquals(10f, result.x, EPSILON);
        assertEquals(20f, result.y, EPSILON);
    }

    @Test
    public void lerp_atHalf_returnsMidpoint() {
        Vector2 a = new Vector2(0f, 0f);
        Vector2 b = new Vector2(10f, 20f);
        Vector2 result = Vector2.Lerp(a, b, 0.5f);
        assertEquals(5f, result.x, EPSILON);
        assertEquals(10f, result.y, EPSILON);
    }

    // ============== ANGLE ==============

    @Test
    public void getFromAngleDegrees_0_isRight() {
        Vector2 v = Vector2.GetFromAngleDegrees(0f);
        assertEquals(1f, v.x, EPSILON);
        assertEquals(0f, v.y, EPSILON);
    }

    @Test
    public void getFromAngleDegrees_90_isUp() {
        Vector2 v = Vector2.GetFromAngleDegrees(90f);
        assertEquals(0f, v.x, EPSILON);
        assertEquals(1f, v.y, EPSILON);
    }

    @Test
    public void getFromAngleDegrees_180_isLeft() {
        Vector2 v = Vector2.GetFromAngleDegrees(180f);
        assertEquals(-1f, v.x, EPSILON);
        assertEquals(0f, v.y, 0.01f);
    }

    @Test
    public void getFromAngleDegrees_resultIsUnitLength() {
        Vector2 v = Vector2.GetFromAngleDegrees(37f);
        assertEquals(1f, Vector2.Magnitude(v), EPSILON);
    }

    // ============== IMMUTABILITY ==============

    @Test
    public void operations_dontModifyOriginals() {
        Vector2 a = new Vector2(1f, 2f);
        Vector2 b = new Vector2(3f, 4f);

        Vector2.add(a, b);
        assertEquals(1f, a.x, EPSILON);
        assertEquals(2f, a.y, EPSILON);
        assertEquals(3f, b.x, EPSILON);
        assertEquals(4f, b.y, EPSILON);

        Vector2.subtract(a, b);
        assertEquals(1f, a.x, EPSILON);

        Vector2.multiply(a, 5f);
        assertEquals(1f, a.x, EPSILON);

        Vector2.Normalize(a);
        assertEquals(1f, a.x, EPSILON);
    }
}
