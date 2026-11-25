CONTEXT
We are Klean (Janz Technologies Sdn Bhd). The Wira Kitar Recycler Dashboard already records every bag collection centres buy from informal waste collectors, with weights from the scale, nightly blockchain anchoring, corrections, and producer EPR reports. We now want the dashboard to support conformity with ISO 59014 (sustainability and traceability of secondary materials recovery), which covers traceability from source to recycler and the protection and inclusion of informal workers ("individuals in subsistence activities"). Auditors, producers and MAREA/KPKT will check these records.

Build this in stages, pausing after each part so I can test. Keep everything already built (scale capture, visits and bags, advances, cash float, anchoring, corrections, reports) working exactly as it is. Bahasa Malaysia first with an English toggle, large touch targets for centre screens, as before.

PART 1. MATERIALS AND DELIVERY RECORDS (traceability upstream)
1. Material list: add to each material
   - hs_code (text, e.g. "3915.90" for plastic waste; admin editable),
   - attributes (list of short tags, e.g. "clear", "coloured", "baled", "contaminated"),
   - quality_criteria (short text), recovery_route_default ("recycling" | "reuse" | "disposal").
2. Each visit (delivery from a collector) also stores:
   - origin_area: where the material was collected (pick from a list of areas/taman per centre, or "Tidak diketahui" / unknown),
   - delivery_ref: a unique reference, auto-generated (e.g. C003-20261125-0042), printed on the receipt,
   - attributes per bag (optional quick tags),
   - recovery_route per bag (defaults from the material; staff can change to "disposal" for rejects, with a reason).
3. If origin is unknown, require a one-tap reason ("collector tidak pasti", "campuran banyak kawasan", other). Show the % of kg with unknown origin per centre in reports.

PART 2. OUTBOUND SALES AND MASS BALANCE (traceability downstream)
1. New screen "Jualan keluar" (Outbound sales): record each sale of material from the centre to a buyer.
   - buyer (pick from a Buyers list: name, address, licence number, licence expiry, contact, type: recycler / aggregator / other),
   - date, material, weight_kg (from the scale if weighed at the centre, or typed with source "buyer weighbridge"),
   - buyer weighbridge ticket number and photo upload, invoice/receipt photo upload, delivery order number,
   - vehicle plate, shipment address,
   - outbound_ref auto-generated (e.g. C003-OUT-20261125-007).
   - Rejects reported by the buyer: kg rejected and reason.
2. Buyers list: warn (red badge) when a buyer's licence has expired or is missing; such sales are excluded from EPR and credit totals until fixed.
3. Monthly mass balance per centre and material:
   opening stock + kg bought from collectors - kg sold out - kg sent to disposal = expected closing stock,
   then staff enter a counted closing stock (estimate allowed, with a photo of the stockpile).
   Show the difference in kg and %. Over 10% difference: flag for Klean admin review with a required explanation.
4. Reports: % of kg bought that reached a licensed recycler, % sent to disposal, and the recycling rate (kg recycled / kg collected) per centre per month. EPR and credit totals by default use the lower of kg bought and kg delivered to licensed recyclers.
5. Include outbound sales in the nightly anchoring export as a second record type, with fields: record_type "outbound", record_id (the outbound_sale id), outbound_ref, centre_id, buyer_id, material, weight_kg, weight_source, ticket_no, date (YYYY-MM-DD), salt. (I will update the anchoring job to accept this type.)

PART 3. CODE OF CONDUCT AND AGREEMENTS
1. Admin uploads the Wira Kitar Code of Conduct (PDF, versioned) and a short Bahasa Malaysia summary.
2. Centres and buyers must accept the current version before they can record sales or appear in reports: store who accepted, role, date, version. Show "Belum terima" (not yet accepted) badges.
3. Collectors: at registration, staff show the one-page collector notice (Malay and English, with a "read aloud" button) and record consent (tap "Setuju" or WhatsApp "YA"), with date and version. Re-consent required when the version changes.
4. Record each collector's age confirmation (18 or over) at registration. If a staff member suspects a child is selling, there is a one-tap "Laporkan kebimbangan" (report concern) that creates a safeguarding case for Klean admin. Never store anything else about the child.

PART 4. GRIEVANCES (complaints)
1. "Aduan" screen and a public WhatsApp/QR entry point: anyone (collector, centre staff, community member) can submit a complaint, anonymously if they want. Categories: payment, weight dispute, safety, harassment or discrimination, environment/nuisance, other.
2. Each complaint gets a reference, status (baru, dalam tindakan, selesai), owner, response, and dates. Target response within 14 days; overdue ones are highlighted.
3. A collector can open a weight dispute directly from a receipt; it links to that visit and bag.
4. Monthly summary for the auditor: count by category, average days to close, open cases. No personal details in the summary.

PART 5. HEALTH, SAFETY AND RISK
1. Risk register per centre: risk, type (environmental, social, health and safety), likelihood (1-5), impact (1-5), controls in place, owner, review date. Provide a starter list: cuts from sharp scrap, heavy lifting, vehicle movement, fire at stockpile, heat, rats and disease, contaminated containers, flooding.
2. Incident log: date, type (injury, near miss, fire, spill, complaint from community), people affected (workers or collectors, no names required), description, actions taken, photo. Serious incidents notify Klean admin at once.
3. Site checklist (monthly, 2 minutes on the tablet): PPE available, first-aid kit stocked, toilet and water accessible to workers and collectors, fire extinguisher in date, stock stored safely, emergency contact sign displayed. Score per centre over time.
4. Emergency contacts page per centre (bomba, ambulance, Klean contact), printable as a poster.

PART 6. TRAINING, PPE AND COLLECTOR SUPPORT
1. Training records: course (safety, sorting, first aid, code of conduct, SOP), date, trainer, attendees (centre staff and collectors by collector ID), language used, certificate yes/no. Collectors' profiles show "Terlatih" badges.
2. PPE issued: collector ID, items (gloves, mask, vest, boots), date, sponsor (brand) if any.
3. Collector support and formalisation status on each profile: registered on Klean app, Touch 'n Go linked, PERKESO self-employed enrolled (yes/no/in progress), member of a cooperative or association (name), trained, PPE issued. Show a simple progress ring.
4. Reports: number and % of active collectors trained, with PPE, enrolled in social protection, in a cooperative; and average monthly income from Wira Kitar sales per active collector (from visits), shown as an aggregate only.

PART 7. MONITORING, TARGETS AND SHARING RESULTS
1. Targets page (admin): per centre and per year, targets for recycling rate, % kg traced to licensed recycler, % collectors trained, % with PPE, grievance response time, site checklist score.
2. Monthly scorecard per centre showing actual vs target and trend; Klean admin can add an action plan item when a target is missed (action, owner, due date, status).
3. Share results: a one-page monthly summary for each centre in Bahasa Malaysia (printable and WhatsApp-able) for staff and collectors, showing kg recycled, recycling rate, safety record and what changed. Workers must be able to see monitoring results.
4. Baseline: a one-off "Baseline" form per centre (before Wira Kitar): monthly kg by material for up to 12 months, source of the figures, and notes. Lock once confirmed.

PART 8. PUBLIC TRACEABILITY POLICY AND DATA GAPS
1. A public page (no login) at /traceability showing: the Wira Kitar traceability policy text (admin editable), how records are anchored on the blockchain, a link to the record check page, and aggregate figures (kg recovered by material per month, number of active centres, number of active collectors, recycling rate). No personal data, no prices, no centre names unless the centre agreed (a per-centre "show name publicly" switch).
2. Data gap log: whenever required traceability data is missing (unknown origin, missing buyer ticket, missing licence), record the gap with the justification chosen, so the auditor can see why. Report % of kg with complete traceability per centre per month.

DATA MODEL (add or adjust)
- material: hs_code, attributes, quality_criteria, recovery_route_default.
- visit: origin_area, origin_unknown_reason, delivery_ref.
- visit_bag: attributes, recovery_route, disposal_reason.
- buyer: id, name, address, licence_no, licence_expiry, type, contact, code_of_conduct_version, accepted_at.
- outbound_sale: id, outbound_ref, centre_id, buyer_id, date, material_id, weight_kg, weight_source, ticket_no, ticket_photo, invoice_photo, do_number, vehicle_plate, shipment_address, rejected_kg, reject_reason, salt, anchor fields as for bags.
- stock_count: centre_id, month, material_id, opening_kg, bought_kg, sold_kg, disposed_kg, expected_kg, counted_kg, difference_pct, explanation, photo.
- code_of_conduct: version, file, summary_ms, effective_from. coc_acceptance: party_type, party_id, version, accepted_by, accepted_at.
- collector_consent: collector_id, notice_version, method, accepted_at, age_confirmed.
- safeguarding_case, grievance, risk, incident, site_checklist, training_session, training_attendee, ppe_issue, collector_support_status, target, action_item, baseline, data_gap.
- Keep audit_log for edits to any of these after creation.

ACCESS
- Centre staff: their centre's visits, outbound sales, stock counts, incidents, checklists, training and PPE records.
- Centre owner: the above plus risk register, targets view and monthly summary.
- Klean admin: everything, all centres, safeguarding cases, grievance owner assignment, policy and code of conduct uploads.
- Producers: unchanged (verified kg and aggregates only), plus recycling rate and % traced.
- Public: the /traceability page only.

DONE WHEN
- For any kg in an EPR report I can trace: collector ID and origin area, the visit and bag (scale reading), the outbound sale to a licensed recycler (ticket photo), and the blockchain anchor, in no more than 3 clicks.
- Each centre shows a monthly mass balance and recycling rate, with differences over 10% flagged.
- Collectors' consent, training, PPE and support status are recorded and reported as percentages.
- A complaint can be submitted from a QR code without login and tracked to closure.
- The public /traceability page shows the policy and live aggregate figures without any personal data.
