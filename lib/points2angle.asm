; Given two points in 2 dimensions, figure the angle
;
; Features:
;   16-bit signed integer math, using radians*100
;   0xSCA standards compliant
;   Adheres to the 0x10cStandardsCommittee ABI
;
; Link: https://github.com/dankuck/foo-dcpu16/blob/master/lib/points2angle.asm
; Libraries:
;       https://github.com/dankuck/foo-dcpu16/blob/master/lib/trig.asm
;       https://github.com/dankuck/foo-dcpu16/blob/master/lib/sqrt16.asm
;
; Author: while1dan

.ifndef points2angle

.include "lib/trig.asm"
.include "lib/sqrt16.asm"

:points2angle	; (A = x1, B = y1, C = x2, [SP+1] = y2)
	SUB A, C
	SET C, SP
	SUB B, [C+1]
	neg(A)
	neg(B)
	SET PC, point2angle
	
; same as points 2 angle, if one point is 0,0
:point2angle	; (A = x, B = y)
	SET PUSH, X
	SET PUSH, Y
	SET PUSH, Z
	SET PUSH, I
	SET PUSH, J
	
	SET I, A
	SET J, B
	
	JSR abs		; x^2
	SET X, A
	MUL X, X
	
	SET A, B	; y^2
	JSR abs
	SET Y, A
	MUL Y, Y
	
	SET Z, X	; z = sqrt(x^2 + y^2)
	ADD Z, Y
	SET A, Z
	JSR sqrt16
	SET Z, A
	
	SET A, J	; 100*vertical/distance
	SET B, 100
	JSR smul
	SET B, Z
	JSR sdiv
	SET J, A

	SET A, J
	JSR asin
	SET J, A
	
	SET A, J
	IFG 0x8000, I
		SET PC, _done_resign
	JSR reflect_angle_horizontally
	:_done_resign
	:_return
	SET J, POP
	SET I, POP
	SET Z, POP
	SET Y, POP
	SET X, POP
	SET PC, POP

:angle2point ; (A = angle, B = distance)
	SET PUSH, X
	SET PUSH, Y
	SET PUSH, Z
	SET Y, A
	SET Z, B
	
	JSR cos
	SET B, Z
	JSR smul
	SET B, 100
	JSR sdiv
	SET X, A
	
	SET A, Y
	JSR sin
	SET B, Z
	JSR smul
	SET B, 100
	JSR sdiv
	SET Y, A
	
	SET A, X
	SET B, Y
	SET POP, Z
	SET POP, Y
	SET POP, X
	SET PC, POP

.end
