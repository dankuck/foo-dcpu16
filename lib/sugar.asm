; Syntactic sugar
;
; Would you rather say pop(X) than SET X, POP?
; This is what you want.
;
; Author: while1dan


.ifndef pop
.macro pop(target){
	SET target, POP
}
.end


.ifndef push
.macro push(target){
	SET PUSH, target
}
.end


.ifndef return
.macro return(){
	SET PC, POP
}
.end
