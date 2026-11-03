# Legacy source follow-ups

The standards cleanup removes speculative inline work markers and obsolete commented-out implementations. This checklist preserves the unfinished questions in repository task tracking. None of the entries below is represented as an implemented feature or a newly reproduced defect. Changes to supported semantics require their own contract and acceptance coverage.

- [ ] `QAppMetaData`: determine whether supported native consumers require `supplementalAppMetaData`; define a typed contract and fixtures before adding it.
- [ ] `QFieldMetaData`: inventory field behavior metadata actually needed by native controls; avoid an untyped `List<Any>` placeholder.
- [ ] `QInstance`: define native report support and a typed report contract before exposing report metadata.
- [ ] `QTableMetaData`: inventory omitted table fields against the supported semantic UI API and add required fields with sanitized real fixtures.
- [ ] `QWidgetMetaData`: inventory omitted widget fields against supported native widgets and add required fields with fixtures.
- [ ] `ProcessComponentHelpText`: decide whether preview/expand behavior is part of the supported contract; test it if introduced.
- [ ] `ProcessComponentWidget.buildProcessComponentWidgetState`: define metadata-driven header/body/footer placement before restoring any list-position heuristic. The deleted implementation was inactive.
- [ ] `ProcessComponentWidget.evaluateWidgetBlocks`: reconcile legacy `INPUT_FIELD` value propagation with the native input renderer; do not restore commented web-client code without contract coverage.
- [ ] `CompositeWidgetBlock.Modal.controlCallback`: confirm whether the semantic callback means toggle or an explicit target visibility; test repeated callbacks against that contract.
- [ ] `ProcessViewModel.closeProcessCallback`: review current guarded navigation and cancellation coverage before changing reverse-navigation notification. The old comment predates the guarded navigation implementation and does not establish a current failure.
- [ ] `ProcessViewModel` widget event handling: verify permitted action-code validation against server metadata; define a fail-closed native check if the supported contract requires it.
- [ ] `QViewModel.loadQInstance`: derive frontend name/version from the declared client build identity instead of the existing fixed identification strings, with compatibility request coverage.

The removed alternate network return and speculative session-test wait snippet are obsolete inactive implementations, retained in Git history rather than executable source. Their deletion changes no runtime behavior. Runtime, provider and physical-device acceptance gates remain tracked in the main task list and readiness documents.
