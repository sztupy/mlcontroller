    .text
    .align
    .global yuv422_2_rgb8888_neon
    .type   yuv422_2_rgb8888_neon, %function
    .global yuv422_2_rgb8888_neon_grayscale
    .type   yuv422_2_rgb8888_neon_grayscale, %function

/* Constants */
#define coef_y         D0
#define coef_v_r       D1
#define coef_u_g       D2
#define coef_v_g       D3
#define coef_u_b       D4
#define coef_x         D5
/* D5 is spare */        
#define bias_r         Q3
#define bias_r_lo      D6
#define bias_r_hi      D7
#define bias_g         Q4
#define bias_g_lo      D8
#define bias_g_hi      D9
#define bias_b         Q5
#define bias_b_lo      D10
#define bias_b_hi      D11

/* Input data */
#define v              D24
#define y_even         D25
#define u              D26
#define y_odd          D27

/* Chrominance signal for whole 16x2 block */
#define r_delta        Q6
#define g_delta        Q7
#define b_delta        Q8

/* Current group of 8 pixels */
#define red            Q9
#define grn            Q10
#define blu            Q11
#define y_scale        Q15

/* output area, in the right order for interleaved output with VST4 */
#define blu8_e         D24
#define red8_e         D25
#define blu8_o         D26
#define red8_o         D27
#define grn8_e         D28
#define alp8_e         D29
#define grn8_o         D30
#define alp8_o         D31

/* ARM registers */
#define rgb_t_ptr      r0
#define y_t_ptr        r1
#define width          r2
#define height         r3
#define yuv_pitch      r4
#define rgb_pitch      r5
#define count          r9
#define aligned_count  sl
#define rgb_b_ptr      fp
#define y_b_ptr        ip
        
/* Constants */
/* 8-bit constants can be loaded into vectors using VMOV */
#define C_Y_SCALE      74   /* Y scale , 74 */
#define C_V_RED        102  /* v -> red coefficient, 102 */
#define C_U_GREEN      25   /* u -> green , -25 */
#define C_V_GREEN      52   /* v -> green , -52 */
#define C_U_BLUE       129  /* u -> blue, +129 */

/* Coefficients */
coefficients:
coeff_bias_r:
    .short  -14240  /* bias_r = 74 * (-16)                + (102 * -128) */
                    /*          -1,184                    + -13,056      */
coeff_bias_g:
    .short    8672  /* bias_g = 74 * (-16) -  25 * (-128) - ( 52 * -128) */
                    /*          -1,184     -  -3200       - -6,656       */
coeff_bias_b:
    .short  -17696  /* bias_b = 74 * (-16) + 129 * (-128)                */
                    /*          -1,184     + -16,512                     */
coeff_pad:
    .short       0

#define rgb_ptr     rgb_t_ptr
#define y_ptr       y_t_ptr
        
yuv422_2_rgb8888_neon:
    /*  r0 = dst_ptr */
    /*  r1 = y_ptr */
    /*  r2 = width */
    /*  r3 = height */
    /*  <> = yuv_pitch */
    /*  <> = rgb_pitch */
    .fnstart
        PUSH            {r4-r12, lr}         /* 10 words */
        VPUSH           {q4-q7}              /* 4Q -> 16 words */

        LDR             yuv_pitch,  [sp, #26*4]
        LDR             rgb_pitch, [sp, #27*4]
        ADR             lr, coefficients

        /* We can't cope with a width less than 16. Check for that. */
        CMP             width, #16
        VPOPLT          {q4-q7}
        POPLT           {r4-r12, pc}
        
        /* Load up vectors containing the bias values. */
        VLD1.S16        {bias_r_lo[], bias_r_hi[]}, [lr]!
        VLD1.S16        {bias_g_lo[], bias_g_hi[]}, [lr]!
        VLD1.S16        {bias_b_lo[], bias_b_hi[]}, [lr]!
        
        /* Build coefficient vectors containing the same value in each element. */
        VMOV.U8         coef_y, #C_Y_SCALE
        VMOV.U8         coef_v_r, #C_V_RED
        VMOV.U8         coef_u_g, #C_U_GREEN
        VMOV.U8         coef_v_g, #C_V_GREEN
        VMOV.U8         coef_u_b, #C_U_BLUE
        VMOV.U8         coef_x, #128

loop_v_422:
        MOV             aligned_count, width
        ANDS            count, width, #15
        MOVEQ           count, #16

loop_h_422:
        /* Load y_even,u,y_odd,v * 4. */
        VLD4.U8         {v,y_even,u,y_odd}, [y_ptr]
        ADD				y_ptr, count, LSL #1
        PLD				[y_ptr]

		VADD.U8			v, v, coef_x
		VADD.U8			u, u, coef_x

        /* Calculate contribution from chrominance signals. */
        VMULL.U8        r_delta, v, coef_v_r
        VMULL.U8        g_delta, u, coef_u_g
        VMLAL.U8        g_delta, v, coef_v_g
        VMULL.U8        b_delta, u, coef_u_b

        /* Add bias. */
        VADD.S16        r_delta, r_delta, bias_r
        VSUB.S16        g_delta, bias_g, g_delta
        VADD.S16        b_delta, b_delta, bias_b

        /* Even: combine luminance and chrominance. */
        VMULL.U8        y_scale, y_even, coef_y        
        VQADD.S16       red, y_scale, r_delta
        VQADD.S16       grn, y_scale, g_delta
        VQADD.S16       blu, y_scale, b_delta

        /* Even: set up alpha data. */
        VMOV.U8         alp8_e, #0xFF

        /* Even: clamp, rescale and clip colour components to 8 bits. */
        VQRSHRUN.S16    red8_e, red, #6
        VQRSHRUN.S16    grn8_e, grn, #6
        VQRSHRUN.S16    blu8_e, blu, #6

        /* Even: Interleave the colour and alpha components
           ready for storage. */
        VZIP.U8         red8_e, alp8_e
        VZIP.U8         blu8_e, grn8_e

        /* Odd: combine luminance and chrominance. */
        VMULL.U8        y_scale, y_odd, coef_y        
        VQADD.S16       red, y_scale, r_delta
        VQADD.S16       grn, y_scale, g_delta
        VQADD.S16       blu, y_scale, b_delta

        /* Odd: set up alpha data. */
        VMOV.U8         alp8_o, #0xFF

        /* Odd: clamp, rescale and clip colour components to 8 bits. */
        VQRSHRUN.S16    red8_o, red, #6
        VQRSHRUN.S16    blu8_o, blu, #6
        VQRSHRUN.S16    grn8_o, grn, #6

        /* Odd: Interleave the colour and alpha components
           ready for storage. */
        VZIP.U8         red8_o, alp8_o
        VZIP.U8         blu8_o, grn8_o
        
        /* Have we reached the end of the row yet? */
        SUBS            aligned_count, aligned_count, count

        /* Store 16 pixels of ARGB32, interleaving even and odd. */
        VST4.U16        {blu8_e, red8_e, blu8_o, red8_o}, [rgb_ptr]
        ADD             rgb_ptr, count, LSL #1
        VST4.U16        {grn8_e, alp8_e, grn8_o, alp8_o}, [rgb_ptr]
        ADD             rgb_ptr, count, LSL #1

        /* On the second (and subsequent) passes through this code,
           we'll always be working on 16 pixels at once. */
        MOV             count, #16
        BGT             loop_h_422

        /* Update pointers for new row of data. */
        SUB             rgb_ptr, width, LSL #2
        SUB             y_ptr, width, LSL #1
        ADD             rgb_ptr, rgb_pitch
        ADD             y_ptr, yuv_pitch
        
        /* Have we reached the bottom yet? */
        SUBS            height, height, #1
        BGT             loop_v_422

        VPOP            {q4-q7}
        POP             {r4-r12, pc}
        .fnend

yuv422_2_rgb8888_neon_grayscale:
    /*  r0 = dst_ptr */
    /*  r1 = y_ptr */
    /*  r2 = width */
    /*  r3 = height */
    /*  <> = yuv_pitch */
    /*  <> = rgb_pitch */
    .fnstart
        PUSH            {r4-r12, lr}         /* 10 words */
        VPUSH           {q4-q7}              /* 4Q -> 16 words */

        LDR             yuv_pitch,  [sp, #26*4]
        LDR             rgb_pitch, [sp, #27*4]
        ADR             lr, coefficients

        /* We can't cope with a width less than 16. Check for that. */
        CMP             width, #16
        VPOPLT          {q4-q7}
        POPLT           {r4-r12, pc}

loop_v_422_gray:
        MOV             aligned_count, width
        ANDS            count, width, #15
        MOVEQ           count, #16

loop_h_422_gray:
        /* Load y_even,u,y_odd,v * 4. */
        VLD4.U8         {v,y_even,u,y_odd}, [y_ptr]
        ADD				y_ptr, count, LSL #1
        PLD				[y_ptr]

        /* Even: set up alpha data. */
        VMOV.U8         alp8_e, #0xFF
        VMOV.U8			red8_e, y_even
        VMOV.U8			grn8_e, y_even
        VMOV.U8			blu8_e, y_even

        VZIP.U8         red8_e, alp8_e
        VZIP.U8         blu8_e, grn8_e

        VMOV.U8         alp8_o, #0xFF
        VMOV.U8		    red8_o, y_odd
        VMOV.U8		    grn8_o, y_odd
        VMOV.U8		    blu8_o, y_odd

        VZIP.U8         red8_o, alp8_o
        VZIP.U8         blu8_o, grn8_o

        SUBS            aligned_count, aligned_count, count

        VST4.U16        {blu8_e, red8_e, blu8_o, red8_o}, [rgb_ptr]
        ADD             rgb_ptr, count, LSL #1
        VST4.U16        {grn8_e, alp8_e, grn8_o, alp8_o}, [rgb_ptr]
        ADD             rgb_ptr, count, LSL #1

        MOV             count, #16
        BGT             loop_h_422_gray

        SUB             rgb_ptr, width, LSL #2
        SUB             y_ptr, width, LSL #1
        ADD             rgb_ptr, rgb_pitch
        ADD             y_ptr, yuv_pitch

        SUBS            height, height, #1
        BGT             loop_v_422_gray

        VPOP            {q4-q7}
        POP             {r4-r12, pc}
        .fnend
