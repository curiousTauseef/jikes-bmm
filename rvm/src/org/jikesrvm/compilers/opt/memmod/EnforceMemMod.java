package org.jikesrvm.compilers.opt.memmod;

import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.LocalCSE;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.util.Stack;

public class EnforceMemMod extends CompilerPhase {

  @Override
  public String getName() {
    return "Chosen Memory Model Enforcement";
  }

  @Override
  public void perform(IR ir) {
    if (VM.BuildForIA32 && (VM.MemoryModel == VM.BMM)
        && VM.MemoryModel_NaiveImpl)
      IA32_BMM_Naive(ir);
    if (VM.BuildForIA32 && (VM.MemoryModel == VM.BMM)
        && !VM.MemoryModel_NaiveImpl)
      IA32_BMM(ir);
    if (VM.BuildForIA32 && (VM.MemoryModel == VM.SC)
        && VM.MemoryModel_NaiveImpl)
      IA32_SC_Naive(ir);
    if (VM.BuildForIA32 && (VM.MemoryModel == VM.SC)
        && !VM.MemoryModel_NaiveImpl)
      IA32_SC(ir);
    if (VM.BuildForPowerPC && (VM.MemoryModel == VM.BMM)
        && VM.MemoryModel_NaiveImpl)
      PPC_BMM_Naive(ir);
    if (VM.BuildForPowerPC && (VM.MemoryModel == VM.BMM)
        && !VM.MemoryModel_NaiveImpl)
      PPC_BMM(ir);
    if (VM.BuildForPowerPC && (VM.MemoryModel == VM.SC)
        && VM.MemoryModel_NaiveImpl)
      PPC_SC_Naive(ir);
    if (VM.BuildForPowerPC && (VM.MemoryModel == VM.SC)
        && !VM.MemoryModel_NaiveImpl)
      PPC_SC(ir);
    VM._assert(VM.NOT_REACHED);
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
    while ((curBlock = worklist.pop()) != null) {
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
        boolean isFirstInst = true;
        for (Instruction inst = curBlock.firstInstruction(); inst != curBlock
            .lastInstruction(); inst = inst.nextInstructionInCodeOrder()) {
          if (LocalCSE.isJavaLoad(inst)) {
            if (isFirstInst) {
              curBBInfo.loadFirst = true;
            }
            // a load should be fenced before if there is open store
            if (shouldFence) {
              inst.insertBefore(Empty.create(FENCE));
              shouldFence = false;
            }
            curBBInfo.noLoadStore = false;
          } else if (LocalCSE.isJavaStore(inst)) {
            // a new open store
            shouldFence = true;
            curBBInfo.noLoadStore = false;
          } else if (inst.getOpcode() == MONITORENTER_opcode ||
              inst.getOpcode() == MONITOREXIT_opcode ||
              inst.getOpcode() == FENCE_opcode) {
            // these instructions provide full fence semantics, so previous
            // store is closed if it is open
            shouldFence = false;
            curBBInfo.noLoadStore = false;
          }
          if (isFirstInst) {
            isFirstInst = false;
          }
        }
      }
      // now the current BB is processed, take care of its successors
      Enumeration<BasicBlock> outs = curBlock.getOut();
      while (outs.hasMoreElements()) {
        BasicBlock outBB = outs.nextElement();
        curBBInfo = bbInfo[outBB.getIndex()];
        if (!curBBInfo.processed) {
          // if the successor is not processed yet, put it in the work list
          curBBInfo.shouldFenceEntry = shouldFence;
          worklist.push(outBB);
        } else if (shouldFence) {
          // it is processed already, but we have new fence request ...
          if (outBB == ir.cfg.exit()) {
            // conservatively insert a fence at the exit of the function
            curBlock.appendInstruction(Empty.create(FENCE));
          } else if (curBBInfo.loadFirst) {
            // put a fence to close the store against a following load
            outBB.prependInstruction(Empty.create(FENCE));
            curBBInfo.loadFirst = false;
          } else if (curBBInfo.noLoadStore
              && !curBBInfo.shouldFenceEntry) {
            // if there is no load, store, or sync in the successor, it just
            // passed over the fencing request. Since we changed it, all its
            // successors should be relooked at and this is the only scenario
            // where a BB being put in the work list twice.
            curBBInfo.shouldFenceEntry = true;
            worklist.push(outBB);
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
    boolean noLoadStore = true;
    boolean shouldFenceEntry = false;
  }
}
