# Phase 9 offline marketplace evidence

The screenshots show an Expo Web export in Chrome talking over HTTP to Spring Boot with disposable H2. The browser used a loopback proxy for the API. The retailer and geocoder adapters returned synthetic test fixtures here. Prices, product results and coordinates in these images are test evidence.

| Screen | Capture |
| --- | --- |
| Fruits | [Desktop](1440x900-Fruits.png) |
| Vegetables | [Desktop](1440x900-Vegetables.png) |
| Milk and Dairy | [Desktop](1440x900-Milk-and-Dairy.png) |
| Snacks | [Desktop](1440x900-Snacks.png) |
| Typed Amul Taaza search | [Desktop](1440x900-typed-search.png) |
| Previously retrieved milk after reload | [Desktop](1440x900-marketplace.png) · [Laptop](1366x768-marketplace.png) · [Mobile](390x844-marketplace.png) |
| Exact cart pack in weekly planning | [Desktop](1440x900-plan.png) |

[HTTP evidence](browser-evidence.json) includes methods, routes, status codes and CartIQ request IDs. [Database rehearsal](mysql-rehearsal.json) covers disposable MySQL and the V18-to-V19 upgrade.

The independent historical Blinkit capture test used an existing operator attachment. The new marketplace did not collect a retailer response from the live provider.
