package org.jikesrvm.compilers.opt.memmod;

import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.RETURN_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.ATHROW_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_opcode;

import java.lang.reflect.Constructor;
import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.LocalCSE;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.util.Stack;

public class EnforceMemMod extends CompilerPhase {

  private static final boolean DEBUG_IA32_SC_NAIVE = false;

  @Override
  public boolean shouldPerform(OptOptions options) {
    return VM.MemoryModel != VM.JMM;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(EnforceMemMod.class, new Class[] {});

  /**
   * Get a constructor object for this compiler phase
   * 
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  @Override
  public String getName() {
    return "Chosen Memory Model Enforcement";
  }

  @Override
  public void perform(IR ir) {
    if (VM.VerifyAssertions)
      VM._assert(VM.MemoryModel != VM.JMM);
    if (VM.BuildForIA32 && (VM.MemoryModel == VM.BMM)
        && VM.MemoryModel_NaiveImpl)
      IA32_BMM_Naive(ir);
    else if (VM.BuildForIA32 && (VM.MemoryModel == VM.BMM)
        && !VM.MemoryModel_NaiveImpl)
      IA32_BMM(ir);
    else if (VM.BuildForIA32 && (VM.MemoryModel == VM.SC)
        && VM.MemoryModel_NaiveImpl)
      IA32_SC_Naive(ir);
    else if (VM.BuildForIA32 && (VM.MemoryModel == VM.SC)
        && !VM.MemoryModel_NaiveImpl)
      IA32_SC(ir);
    else if (VM.BuildForPowerPC && (VM.MemoryModel == VM.BMM)
        && VM.MemoryModel_NaiveImpl)
      PPC_BMM_Naive(ir);
    else if (VM.BuildForPowerPC && (VM.MemoryModel == VM.BMM)
        && !VM.MemoryModel_NaiveImpl)
      PPC_BMM(ir);
    else if (VM.BuildForPowerPC && (VM.MemoryModel == VM.SC)
        && VM.MemoryModel_NaiveImpl)
      PPC_SC_Naive(ir);
    else if (VM.BuildForPowerPC && (VM.MemoryModel == VM.SC)
        && !VM.MemoryModel_NaiveImpl)
      PPC_SC(ir);
    else
      VM._assert(VM.NOT_REACHED, "Unknown config: MM [" + VM.MemoryModel
          + "] Naive[" + VM.MemoryModel_NaiveImpl + "]");
  }

  private void IA32_BMM_Naive(IR ir) {
    // no fence needed
  }

  private void IA32_BMM(IR ir) {
    // no fence needed
  }

  /**
   * Insert fences between store and load
   * 
   * @param ir
   */
  private void IA32_SC_Naive(IR ir) {
    Stack<BasicBlock> worklist = new Stack<BasicBlock>();
    BBInfo_IA32_SC_Naive[] bbInfo = new BBInfo_IA32_SC_Naive[ir.cfg
        .numberOfNodes()];
    for (int i = 0; i < ir.cfg.numberOfNodes(); i++) {
      bbInfo[i] = new BBInfo_IA32_SC_Naive();
    }

    BasicBlock curBlock = ir.cfg.entry();
    worklist.push(curBlock);
    BBInfo_IA32_SC_Naive curBBInfo = null;
    while (!worklist.isEmpty()) {
      curBlock = worklist.pop();
      if (curBlock.isExit())
        continue;
      if (DEBUG_IA32_SC_NAIVE) {
        VM.sysWriteln("Processing: " + curBlock);
      }
      // if a fence should be placed before hitting the next load. This is only
      // true when there are previously open stores (open store: there is no
      // load or sync instruction between from the store to the current point )
      boolean shouldFence = false;
      curBBInfo = bbInfo[curBlock.getIndex()];
      // Unlike classic data-flow analysis which ends with fix-point, our BB
      // will be processed at most twice: if it 1) has shouldFenceEntry being
      // false at the first time and 2) loadFirst being true, then whenever one
      // of its predecessors left with an open store.
      if (!curBBInfo.processed) {
        curBBInfo.processed = true;
        shouldFence = curBBInfo.shouldFenceEntry;
        // only load, store, and sync concerned
        boolean isFirstConcernedInst = true;
        for (Instruction inst = curBlock.firstInstruction(); inst != curBlock
            .lastInstruction(); inst = inst.nextInstructionInCodeOrder()) {
          if (DEBUG_IA32_SC_NAIVE) {
            VM.sysWrite("\tinst: " + inst.toString());
          }
          if (LocalCSE.isJavaLoad(inst) || inst.getOpcode() == RETURN_opcode
              || inst.getOpcode() == ATHROW_opcode
              || inst.getOpcode() == CALL_opcode) {
            // a load should be fenced if there is open store
            // return, throw, and call should be conservatively fenced
            if (shouldFence) {
              inst.insertBefore(Empty.create(FENCE));
              shouldFence = false;
              if (DEBUG_IA32_SC_NAIVE) {
                VM.sysWrite("\t <-- fence before ");
              }
            }
            if (isFirstConcernedInst) {
              curBBInfo.loadFirst = true;
            }
            curBBInfo.noMemAcc = false;
            isFirstConcernedInst = false;
          } else if (LocalCSE.isJavaStore(inst)) {
            // a new open store
            shouldFence = true;
            curBBInfo.noMemAcc = false;
            isFirstConcernedInst = false;
          } else if (inst.getOpcode() == MONITORENTER_opcode ||
              inst.getOpcode() == MONITOREXIT_opcode ||
              inst.getOpcode() == FENCE_opcode) {
            // syncs close previous stores by providing full fence semantics 
            shouldFence = false;
            curBBInfo.noMemAcc = false;
            isFirstConcernedInst = false;
          }
          if (DEBUG_IA32_SC_NAIVE) {
            VM.sysWriteln();
          }
        }
      }
      if (DEBUG_IA32_SC_NAIVE) {
        VM.sysWriteln("\tInfo: [ " + curBBInfo
            + (shouldFence ? "shouldFence ]" : "]"));
      }

      // now the current BB is processed, take care of its successors
      Enumeration<BasicBlock> outs = curBlock.getOut();
      while (outs.hasMoreElements()) {
        BasicBlock outBB = outs.nextElement();
        curBBInfo = bbInfo[outBB.getIndex()];
        if (!curBBInfo.processed) {
          // if the successor is not processed yet, process as normally
          curBBInfo.shouldFenceEntry = shouldFence;
          worklist.push(outBB);
          if (DEBUG_IA32_SC_NAIVE) {
            VM.sysWriteln("Push: " + outBB);
          }
        } else if (shouldFence) {
          // it is processed already, but we have new fence request ...
          if (curBBInfo.loadFirst) {
            // put a fence to close the store against a following load
            outBB.prependInstruction(Empty.create(FENCE));
            curBBInfo.loadFirst = false;
            if (DEBUG_IA32_SC_NAIVE) {
              VM.sysWriteln("Revisit: " + outBB + " by prepending a fence");
            }
          } else if (curBBInfo.noMemAcc
              && !curBBInfo.shouldFenceEntry) {
            // if there is no load, store, or sync in the successor, it just
            // passed over the fencing request. Since we changed it, all its
            // successors should be relooked at and this is the only scenario
            // where a BB being put in the work list twice.
            curBBInfo.shouldFenceEntry = true;
            worklist.push(outBB);
            if (DEBUG_IA32_SC_NAIVE) {
              VM.sysWriteln("Revisit: " + outBB
                  + " by pushing back to work list");
            }
          }
        }
      }
    }
  }

  private void IA32_SC(IR ir) {
    throw new Error("Not implemented yet");
  }

  private void PPC_BMM_Naive(IR ir) {
    throw new Error("Not implemented yet");
  }

  private void PPC_SC_Naive(IR ir) {
    throw new Error("Not implemented yet");
  }

  private void PPC_BMM(IR ir) {
    throw new Error("Not implemented yet");
  }

  private void PPC_SC(IR ir) {
    throw new Error("Not implemented yet");
  }

  private static final class BBInfo_IA32_SC_Naive {
    boolean processed = false;
    boolean loadFirst = false;
    boolean noMemAcc = true;
    boolean shouldFenceEntry = false;

    public String toString() {
      String s = "";
      s += loadFirst ? "loadFirst " : "";
      s += noMemAcc ? "noMemAcc " : "";
      s += shouldFenceEntry ? "shouldFenceEntry " : "";
      return s;
    }
  }
}
