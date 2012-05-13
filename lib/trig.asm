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

.def pi10     314
.def halfpi10 157
.def twopi10  628

:sin ; (A = angle in radians * 100, result: A = sin * 100)
	JSR normalize_angle
	; set to positive, but remember if it was negative
	SET B, A
	AND B, 0x8000
	IFN B, 0x8000
		SET PC, _done_unsign
	XOR A, 0xFFFF
	ADD A, 1
	:_done_unsign
	; reflect left half of circle onto right half
	IFG halfpi10+1, A
		SET PC, _done_reflect
	SET C, A
	SUB C, halfpi10
	SET A, halfpi10
	SUB A, C
	:_done_reflect
	; get sin from table
	SET A, [sin_table+A]
	; if the original was negative, switch the sign back
	IFN B, 0x8000
		SET PC, _done_resign
	XOR A, 0xFFFF
	ADD A, 1
	:_done_resign
	SET PC, POP
	
:cos ; (A = angle in radians * 100)
	JSR normalize_angle
	ADD A, halfpi10
	SET PC, sin
		
:normalize_angle ; (A = angle in radians * 100)
	; if it's negative, lets turn it positive and work it that way. then we'll negate the answer later
	SET B, A
	AND B, 0x8000
	IFN B, 0x8000
		SET PC, _done_unsign
	XOR A, 0xFFFF
	ADD A, 1
	:_done_unsign
	; put it within 2*PI
	MOD A, twopi10
	; if it's more than PI, reflect it over PI and switch the sign
	IFG pi10+1, A
		SET PC, _done_reflect
	SET C, A
	SUB C, pi10
	SET A, pi10
	SUB A, C
	XOR A, 0xFFFF
	ADD A, 1
	:_done_reflect
	; if it was originally negative, switch the sign
	IFN B, 0x8000
		SET PC, _done_resign
	XOR A, 0xFFFF
	ADD A, 1
	:_done_resign
	SET PC, POP
		

:radians_to_degrees

:degrees_to_radians

	.dw 0; can't do that yet

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

.end
