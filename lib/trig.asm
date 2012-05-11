; Provides trigonometric functions.
; You need some trig to navigate space, my friend.
;
; Uses the Taylor series for sin() and bases everything else off that.
;
; DOESN'T WORK YET
;
; Author: while1dan (aka dankuck)


.ifndef sin

.include "lib/pow_macro.asm"

.define three_fac 6
.define five_fac  120
.define seven_fac 5040

:sin ; (A = angle in radians)
	SET C, A
	SET B, A
	pow(B, 3);
	DIV B, three_fac
	SUB C, B
	SET B, A
	pow(B, 5)
	DIV B, five_fac
	ADD C, B
	SET B, A
	pow(B, 7)
	DIV B, seven_fac
	SUB C, B
	SET A, C
	SET PC, POP

:radians_to_degrees

:degrees_to_radians

	.dw 0; can't do that yet

.end
