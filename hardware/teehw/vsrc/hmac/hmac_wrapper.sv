// Copyright lowRISC contributors.
// Licensed under the Apache License, Version 2.0, see LICENSE for details.
// SPDX-License-Identifier: Apache-2.0

/**
 */
module hmac_wrapper #(
  parameter logic [hmac_reg_pkg::NumAlerts-1:0] AlertAsyncOn = {hmac_reg_pkg::NumAlerts{1'b1}} 
) (
  // Clock and Reset
  input  logic        clk_i,
  input  logic        rst_ni,

  // Instruction memory interface
  input  logic                         tl_a_valid,
  output logic                         tl_a_ready,
  input  logic                  [2:0]  tl_a_bits_opcode,
  input  logic                  [2:0]  tl_a_bits_param,
  input  logic  [top_pkg::TL_SZW-1:0]  tl_a_bits_size,
  input  logic  [top_pkg::TL_AIW-1:0]  tl_a_bits_source,
  input  logic   [top_pkg::TL_AW-1:0]  tl_a_bits_address,
  input  logic  [top_pkg::TL_DBW-1:0]  tl_a_bits_mask,
  input  logic   [top_pkg::TL_DW-1:0]  tl_a_bits_data,
  input  logic                  [6:0]  tl_a_bits_user_tl_a_user_t_rsvd1,
  input  logic                         tl_a_bits_user_tl_a_user_t_parity_en,
  input  logic                  [7:0]  tl_a_bits_user_tl_a_user_t_parity,
  input  logic                         tl_a_bits_corrupt,

  output logic                         tl_d_valid,
  input  logic                         tl_d_ready,
  output logic                  [2:0]  tl_d_bits_opcode,
  output logic                  [2:0]  tl_d_bits_param,
  output logic  [top_pkg::TL_SZW-1:0]  tl_d_bits_size,   // Bouncing back a_size
  output logic  [top_pkg::TL_AIW-1:0]  tl_d_bits_source,
  output logic  [top_pkg::TL_DIW-1:0]  tl_d_bits_sink,
  output logic   [top_pkg::TL_DW-1:0]  tl_d_bits_data,
  output logic  [top_pkg::TL_DUW-1:0]  tl_d_bits_user_uint,
  output logic                         tl_d_bits_corrupt,
  output logic                         tl_d_bits_denied,

  // Interrupts
  output logic                         intr_hmac_done_o,
  output logic                         intr_fifo_empty_o,
  output logic                         intr_hmac_err_o,

  // Alerts
  input  logic                         alert_rx_i_0_ping_p,
  input  logic                         alert_rx_i_0_ping_n,
  input  logic                         alert_rx_i_0_ack_p,
  input  logic                         alert_rx_i_0_ack_n,

  output logic                         alert_tx_o_0_alert_p,
  output logic                         alert_tx_o_0_alert_n

);

  // tl connections

  wire tlul_pkg::tl_h2d_t tl_i;
  wire tlul_pkg::tl_d2h_t tl_o;
  
  assign tl_i.a_valid = tl_a_valid; 
  assign tl_i.a_opcode = tl_a_bits_opcode;
  assign tl_i.a_param = tl_a_bits_param;
  assign tl_i.a_size = tl_a_bits_size;
  assign tl_i.a_source = tl_a_bits_source;
  assign tl_i.a_address = tl_a_bits_address;
  assign tl_i.a_mask = tl_a_bits_mask;
  assign tl_i.a_data = tl_a_bits_data;
  assign tl_i.a_user.rsvd1 = tl_a_bits_user_tl_a_user_t_rsvd1;
  assign tl_i.a_user.parity_en = tl_a_bits_user_tl_a_user_t_parity_en;
  assign tl_i.a_user.parity = tl_a_bits_user_tl_a_user_t_parity;

  assign tl_i.d_ready = tl_d_ready;
  
  // tl_a_bits_corrupt; // ignored
  
  assign tl_d_valid = tl_o.d_valid;
  assign tl_d_bits_opcode = tl_o.d_opcode;
  assign tl_d_bits_param = tl_o.d_param;
  assign tl_d_bits_size = tl_o.d_size;
  assign tl_d_bits_source = tl_o.d_source;
  assign tl_d_bits_sink = tl_o.d_sink;
  assign tl_d_bits_data = tl_o.d_data;
  assign tl_d_bits_user_uint = tl_o.d_user;
  assign tl_d_bits_corrupt = tl_o.d_error; // Seems legit
  assign tl_d_bits_denied = 1'b0; // Seems also legit

  assign tl_a_ready = tl_o.a_ready;
  
  // That "weird" alert port connections

  wire prim_alert_pkg::alert_tx_t[0:0] alert_tx_o;
  wire prim_alert_pkg::alert_rx_t[0:0] alert_rx_i;
  
  assign alert_rx_i[0].ping_p = alert_rx_i_0_ping_p;
  assign alert_rx_i[0].ping_n = alert_rx_i_0_ping_n; 
  assign alert_rx_i[0].ack_p = alert_rx_i_0_ack_p;
  assign alert_rx_i[0].ack_n = alert_rx_i_0_ack_n;
  assign alert_tx_o_0_alert_p = alert_tx_o[0].alert_p;
  assign alert_tx_o_0_alert_n = alert_tx_o[0].alert_n;

  hmac #(
    .AlertAsyncOn             ( AlertAsyncOn             )
  ) u_aes (
    // clock and reset
    .clk_i                (clk_i),
    .rst_ni               (rst_ni),
    // TL-UL buses
    .tl_o                 (tl_o),
    .tl_i                 (tl_i),
    // Interrupts
    .intr_hmac_done_o     (intr_hmac_done_o),
    .intr_fifo_empty_o    (intr_fifo_empty_o),
    .intr_hmac_err_o      (intr_hmac_err_o),
    // Alert
    .alert_rx_i           (alert_rx_i),
    .alert_tx_o           (alert_tx_o)
  );

endmodule
