; Work with signed 16-bit integers using two's complement notation.
;
; ADD, SUB and MUL already work for signed integers, so they are not provided 
; here.
;
; sdiv does signed division
; neg flips the sign on a signed integer
; abs sets the sign to positive on a signed integer
; neg(target) does sign flippage without a JSR
;
; Features:
;   16-bit signed integer math
;   0xSCA standards compliant
;   Adheres to the 0x10cStandardsCommittee ABI
;
; Link: https://github.com/dankuck/foo-dcpu16/blob/master/lib/signedint.asm
; 
; Author: while1dan


.ifndef signed_int_math
.def signed_int_math 1

.macro neg(target){ ; result: target=-target
	XOR target, 0xFFFF
	ADD target, 1
}

.macro sdiv(s1, s2){
	SET A, s1
	SET B, s2
	JSR sdiv
	SET s1, A
}

:sdiv ; (result: A=A/B)
	IFG A, 0x7fff
		SET PC, _a_neg
	IFG B, 0x7fff
		SET PC, _a_pos_b_neg
	:_both_positive
	DIV A, B
	SET PC, POP
	:_a_neg
	IFG B, 0x7fff
		SET PC, _both_neg
	neg(A)
	DIV A, B
	neg(A)
	SET PC, POP
	:_a_pos_b_neg
	neg(B)
	DIV A, B
	neg(A)
	SET PC, POP
	:_both_neg
	neg(A)
	neg(B)
	DIV A, B
	SET PC, POP

:abs ; (result: target=|target|)
	IFG 0x8000, A
		SET PC, POP
	neg(A)
	SET PC, POP
	
:neg ; (result: target=-target)
	XOR A, 0xFFFF
	ADD A, 1
	SET PC, POP
	
	
.end
