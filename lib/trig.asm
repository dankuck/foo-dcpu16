; Provides trigonometric functions.
; You need some trig to navigate space, my friend.
;
; We could use a Taylor series for sin() but it is apt
; to be slow and imprecise when using integers.
;
; So we just use a table. Fast and more precise.
;
; Works on signed 16-bit integers
;
; Author: while1dan (aka dankuck)


.ifndef sin

.include "lib/signedint.asm"

.def pi100     314
.def halfpi100 157
.def twopi100  628

:sin ; (A = angle in radians * 100, result: A = sin * 100)
	JSR normalize_angle
	; set to positive, but remember if it was negative
	SET B, A
	AND B, 0x8000
	IFN B, 0x8000
		SET PC, _done_unsign
	neg(A)
	:_done_unsign
	; reflect left half of circle onto right half
	IFG halfpi100+1, A
		SET PC, _done_reflect
	SET C, A
	SUB C, halfpi100
	SET A, halfpi100
	SUB A, C
	:_done_reflect
	; get sin from table
	SET A, [sin_table+A]
	; if the original was negative, switch the sign back
	IFN B, 0x8000
		SET PC, _done_resign
	neg(A)
	:_done_resign
	SET PC, POP
	
:cos ; (A = angle in radians * 100)
	JSR normalize_angle
	ADD A, halfpi100
	SET PC, sin

:asin ; (A = sin(angle in radians) * 100)
	; set to positive, but remember if it was negative
	SET B, A
	AND B, 0x8000
	IFN B, 0x8000
		SET PC, _done_unsign
	neg(A)
	:_done_unsign
	; die if outside of range
	IFG A, 100
		SET PC, _print_and_die
	SET A, [asin_table+A]
	IFE B, 0x8000
		SET PC, _done_resign
	neg(A)
	:_done_resign
	SET PC, POP

	:_print_and_die
		.ifdef print
			SET A, _message
			JSR print
		.end
		.ifdef crash
			SET PC, crash
		.else
			:_crash SET PC, _crash
		.end
	:_message
		.dw p"Too large number passed to asin or acos."

:acos ; (A = cos(angle in radians) * 100)
	JSR asin
	SUB A, halfpi100
	JSR normalize_angle
	SET PC, POP
	
:tan ; (A = angle in radians * 100)
	SET PUSH, X
	SET PUSH, Y
	SET X, A
	JSR cos
	SET Y, A
	SET A, X
	JSR sin
	IFE Y, 0
		SET PC, _inf
	SET B, 100
	JSR SMUL
	SET B, Y
	JSR SDIV
	SET PC, _return
	:_inf
	IFG A, 0x7FFF
		SET PC, _neg_inf
	SET A, 0x7FFF ; positive infinity
	SET PC, _return
	:_neg_inf
	SET A, -0x7FFF ; negative infinity
	:_return
	SET Y, POP
	SET X, POP
	SET PC, POP
	
	
:normalize_angle ; (A = angle in radians * 100)
	; if it's negative, lets turn it positive and work it that way. then we'll negate the answer later
	SET B, A
	AND B, 0x8000
	IFN B, 0x8000
		SET PC, _done_unsign
	neg(A)
	:_done_unsign
	; put it within 2*PI
	MOD A, twopi100
	; if it's more than PI, reflect it over PI and switch the sign
	IFG pi100+1, A
		SET PC, _done_reflect
	SET C, A
	SUB C, pi100
	SET A, pi100
	SUB A, C
	neg(A)
	:_done_reflect
	; if it was originally negative, switch the sign
	IFN B, 0x8000
		SET PC, _done_resign
	neg(A)
	:_done_resign
	SET PC, POP

:radians_to_degrees ; (A = radians * 100)
	; degrees = 180 * radians / PI
	; degrees = 100 * 180 * radians / (PI * 100)
	;JSR normalize_angle
	SET C, A
	AND C, 0x8000
	IFN C, 0x8000
		SET PC, _done_unsign
	neg(A)
	:_done_unsign
	MUL A, 180
	DIV A, pi100
	IFN C, 0x8000
		SET PC, _done_resign
	neg(A)
	:_done_resign
	SET PC, POP

:degrees_to_radians
	; radians = PI * degrees / 180
	; radians * 100 = 100 * PI * degrees / 180
	SET C, A
	AND C, 0x8000
	IFN C, 0x8000
		SET PC, _done_unsign
	neg(A)
	:_done_unsign
	MUL A, pi100
	DIV A, 180
	IFN C, 0x8000
		SET PC, _done_resign
	neg(A)
	:_done_resign
	JSR normalize_angle
	SET PC, POP
	

:sin_table
.dw 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
.dw 10, 11, 12, 13, 14, 15, 16, 17, 18, 19
.dw 20, 21, 22, 23, 24, 25, 26, 27, 28, 29
.dw 30, 31, 32, 33, 34, 35, 36, 37, 38, 38
.dw 39, 40, 41, 42, 43, 44, 45, 46, 47, 47
.dw 48, 49, 50, 51, 52, 53, 53, 54, 55, 56
.dw 57, 58, 58, 59, 60, 61, 62, 62, 63, 64
.dw 65, 65, 66, 67, 68, 68, 69, 70, 71, 71
.dw 72, 73, 73, 74, 75, 75, 76, 77, 77, 78
.dw 78, 79, 80, 80, 81, 81, 82, 83, 83, 84
.dw 84, 85, 85, 86, 86, 87, 87, 88, 88, 89
.dw 89, 90, 90, 90, 91, 91, 92, 92, 92, 93
.dw 93, 93, 94, 94, 94, 95, 95, 95, 96, 96
.dw 96, 96, 97, 97, 97, 97, 97, 98, 98, 98
.dw 98, 98, 99, 99, 99, 99, 99, 99, 99, 99
.dw 99, 99, 99, 99, 99, 99, 99

:asin_table
.dw 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
.dw 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
.dw 21, 22, 23, 24, 25, 26, 27, 28, 29, 30
.dw 31, 32, 33, 34, 35, 36, 37, 38, 40, 41
.dw 42, 43, 44, 45, 46, 47, 48, 50, 51, 52
.dw 53, 54, 55, 57, 58, 59, 60, 61, 63, 64
.dw 65, 66, 68, 69, 70, 72, 73, 74, 76, 77
.dw 78, 80, 81, 83, 84, 86, 87, 89, 91, 92
.dw 94, 96, 97, 99, 101, 103, 105, 107, 109, 111
.dw 114, 116, 119, 122, 125, 128, 132, 137, 142, 157

.end
