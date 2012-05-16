; Figure how far away a thing is based on its angle and your distance from another thing
;
; Author: while1dan

.ifndef triangulate

.include "lib/point2angle.asm"

; In a triangle formed by the target, the point 0,0 and a helper:
; A = the angle at 0,0
; B = the angle at the helper
; C = the distance to the helper
; returns the distance to the target
:find_distance
	; return (C * sin(B)) / sin(A+B)
	SET PUSH, X
	SET PUSH, Y
	SET PUSH, Z
	SET Y, B
	SET Z, C
	
	ADD A, B
	JSR sin
	SET X, A
	
	SET A, Y
	JSR sin
	SET B, Z
	JSR smul
	SET B, X
	JSR sdiv
	
	:_return
	SET Z, POP
	SET Y, POP
	SET X, POP
	SET PC, POP


.end
