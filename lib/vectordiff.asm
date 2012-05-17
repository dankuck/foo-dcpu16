; Given two vectors, figures the angle between them
;
; Author: while1dan

.ifndef vectordiff

.include "lib/trig.asm"
.include "lib/signedint.asm"

; For two vectors of magnitude 100 (ie, this is just like the *100 stuff in points2angle.asm)
:vectordiff ; (A = i1, B = j1, C = k1, [SP+1] = i2, [SP+2] = j2, [SP+3] = k2)
	; cos(T) = (i1i2 + j1j2 + k1k2)/10000
	SET PUSH, X
	SET PUSH, Y
	SET PUSH, Z
	SET PUSH, I
	SET PUSH, J
	
	SET X, A
	SET Y, B
	SET Z, C
	SET I, SP
	
	MUL X, [I+6]
	MUL Y, [I+7]
	MUL Z, [I+8]
	
	ADD X, Y
	ADD X, Z
	
	SET A, X
	SET B, 100
	JSR sdiv
	JSR acos
	
	:_return
	SET J, POP
	SET I, POP
	SET Z, POP
	SET Y, POP
	SET X, POP
	SET PC, POP


.end
