; Raise a number to a power
; Using a macro is more efficient than using a loop.
; It doesn't require a bunch of SET PC's and it might
; even allow for optimization of some MUL's.
;
; Author: while1dan

.ifndef pow

.macro pow(target, exp){
	.rep exp{
		MUL target, target
	}
}

.end