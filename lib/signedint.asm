; Functions for working with signed 16-bit integers
; For integers in two's complement notation. But really, 
; who would do anything else?
; ADD and SUB already work for signed integers, so 
; they are not provided here.
;
; Author: while1dan


.ifndef signed_int_math
.def signed_int_math 1

:smul ; (multiplies A and B)
	SET PUSH, X
	SET C, A
	AND C, 0x8000
	IFN C, 0x8000
		SET PC, _A_isnt_neg
	XOR A, 0xFFFF
	ADD A, 1
	:_A_isnt_neg
	IFG 0x8000, B
		SET PC, _B_isnt_neg
	XOR B, 0xFFFF
	ADD B, 1
	XOR C, 0xFFFF
	AND C, 0x8000
	:_B_isnt_neg
	MUL A, B
	SHL O, 1
	SET X, A
	SHR X, 15
	BOR O, X
	AND A, 0x7FFF
	IFE C, 0
		SET PC, _done
	XOR A, 0xFFFF
	ADD A, 1
	:_done
	SET X, POP
	SET PC, POP

:sdiv ; (divides A and B)
	SET PUSH, X
	SET C, A
	AND C, 0x8000
	IFN C, 0x8000
		SET PC, _A_isnt_neg
	XOR A, 0xFFFF
	ADD A, 1
	:_A_isnt_neg
	IFG 0x8000, B
		SET PC, _B_isnt_neg
	XOR B, 0xFFFF
	ADD B, 1
	XOR C, 0xFFFF
	AND C, 0x8000
	:_B_isnt_neg
	DIV A, B
	;SHL O, 1
	;SET X, A
	;SHR X, 15
	;BOR O, X
	AND A, 0x7FFF
	IFE C, 0
		SET PC, _done
	XOR A, 0xFFFF
	ADD A, 1
	:_done
	SET X, POP
	SET PC, POP

.macro smul(s1, s2){
	SET A, s1
	SET B, s2
	JSR smul
	SET s1, A
}

.macro sdiv(s1, s2){
	SET A, s1
	SET B, s2
	JSR sdiv
	SET s1, A
}

.macro neg(target){
	XOR target, 0xFFFF
	ADD target, 1
}

:abs
	IFG 0x8000, A
		SET PC, POP
	neg(A)
	SET PC, POP
	
:neg	
	XOR A, 0xFFFF
	ADD A, 1
	SET PC, POP
	
	
.end
