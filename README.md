# MobileBot 🤖

AI Desktop Pet บน Android — ตัวละคร overlay เดินบนหน้าจอ โดนจับ/ลูบ/ปาได้ และ **คิด-พูด-ทำงานแทนคุณได้จริง** ด้วย LLM on-device (ไม่มีโฆษณา, ไม่มี cloud API)

## สถานะโปรเจกต์

| Phase | งาน | สถานะ |
|---|---|---|
| ✅ Research | สำรวจ repo อ้างอิง 3 ตัว | เสร็จ |
| ✅ Model | เลือก+ทดสอบ GGUF function-calling | เสร็จ — **5/5 PASS** |
| 🔜 Phase 1 | เชื่อม LLM เข้า GooseDroid base | กำลังวางแผน |
| ⏳ Phase 2 | Base character ของตัวเอง (ตัวโล้น) | รอ |
| ⏳ Phase 3 | UI interact (สั่งภาษาไทยให้ pet กดจอ) | รอ |

## โมเดลที่ยืนยันแล้ว

**Typhoon2.5-Qwen3-4B Q4_K_M** (`typhoon-ai/typhoon2.5-qwen3-4b-gguf`, 2.5GB)
- Function-calling ภาษาไทย 5/5: open_app / tap-from-UI-tree / input_text / chat-reply / send-tap
- CPU x64: 13.6 tok/s · RAM ~3GB
- Settings ที่ได้ผล: `temp 0.2` + `repeat_penalty 1.15` (จำเป็น — ไม่งั้นวนซ้ำ)

### System prompt rules ที่พิสูจน์แล้ว
1. ตอบ JSON action เดียวต่อ turn
2. "สั่งพิมพ์ → `input_text` เลย ไม่ tap หาช่อง" (ระบบ focus ให้)
3. พิกัดจาก bounds `[x1,y1,x2,y2]` = จุดกึ่งกลาง

## Repo อ้างอิง (clone ไว้ที่ C:/Users/Mynew/)

| Repo | License | เอามาใช้ |
|---|---|---|
| [pocketpal-ai](https://github.com/a-ghorbani/pocketpal-ai) | MIT ✓ | llama.rn engine, model loader |
| [GooseDroid](https://github.com/skyvanguard/GooseDroid) | All rights reserved ⚠️ | สถาปัตยกรรมอ้างอิงเท่านั้น (overlay touch pass-through, 24 gestures, behavior tree) |
| [mobile-use](https://github.com/minitap-ai/mobile-use) | Apache-2.0 ✓ | pattern a11y-tree→LLM→action (credit Minitap) |

## สถาปัตยกรรมเป้าหมาย

```
┌─ Foreground Service ────────────────────────┐
│  Overlay View (full-screen, touch pass-     │
│  through ยกเว้นโซนตัวละคร — เทคนิค GooseView) │
│   ├─ Character render (Canvas procedural)   │
│   ├─ Gesture handler (tap/pet/drag/throw)   │
│   ├─ Behavior tree (needs/personality)      │
│   └─ PetLLM ← Typhoon GGUF via llama.rn     │
│        ├─ thought bubbles (แชทธรรมดา)        │
│        ├─ action JSON (UI automation)       │
│        └─ AccessibilityService dispatch     │
└─────────────────────────────────────────────┘
```

## Build

(Phase 1 — จะอัปเดตเมื่อมี skeleton app)

## ทีม

- **ธนดล (Thanadol)** — developer
- AI assistant pair-programming via Hermes

---
*Goal: MVP ปิดยอด ~3,000฿ ใน 2 สัปดาห์ · ขาย unlock ครั้งเดียวผ่าน PromptPay*
