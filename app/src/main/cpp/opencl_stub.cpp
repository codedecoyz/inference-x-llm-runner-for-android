#include <CL/cl.h>

// Mock functions to satisfy the linker at compile time.
// At runtime, the actual libOpenCL.so provided by the Android device will be used.

extern "C" {
    cl_int clGetPlatformIDs(cl_uint num_entries, cl_platform_id *platforms, cl_uint *num_platforms) { return 0; }
    cl_int clGetPlatformInfo(cl_platform_id platform, cl_platform_info param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return 0; }
    cl_int clGetDeviceIDs(cl_platform_id platform, cl_device_type device_type, cl_uint num_entries, cl_device_id *devices, cl_uint *num_devices) { return 0; }
    cl_int clGetDeviceInfo(cl_device_id device, cl_device_info param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return 0; }
    cl_context clCreateContext(const cl_context_properties *properties, cl_uint num_devices, const cl_device_id *devices, void (*pfn_notify)(const char *, const void *, size_t, void *), void *user_data, cl_int *errcode_ret) { return 0; }
    cl_int clReleaseContext(cl_context context) { return 0; }
    cl_command_queue clCreateCommandQueue(cl_context context, cl_device_id device, cl_command_queue_properties properties, cl_int *errcode_ret) { return 0; }
    cl_command_queue clCreateCommandQueueWithProperties(cl_context context, cl_device_id device, const cl_queue_properties *properties, cl_int *errcode_ret) { return 0; }
    cl_int clReleaseCommandQueue(cl_command_queue command_queue) { return 0; }
    cl_mem clCreateBuffer(cl_context context, cl_mem_flags flags, size_t size, void *host_ptr, cl_int *errcode_ret) { return 0; }
    cl_int clReleaseMemObject(cl_mem memobj) { return 0; }
    cl_program clCreateProgramWithSource(cl_context context, cl_uint count, const char **strings, const size_t *lengths, cl_int *errcode_ret) { return 0; }
    cl_int clBuildProgram(cl_program program, cl_uint num_devices, const cl_device_id *device_list, const char *options, void (*pfn_notify)(cl_program, void *), void *user_data) { return 0; }
    cl_int clGetProgramBuildInfo(cl_program program, cl_device_id device, cl_program_build_info param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return 0; }
    cl_int clReleaseProgram(cl_program program) { return 0; }
    cl_kernel clCreateKernel(cl_program program, const char *kernel_name, cl_int *errcode_ret) { return 0; }
    cl_int clReleaseKernel(cl_kernel kernel) { return 0; }
    cl_int clSetKernelArg(cl_kernel kernel, cl_uint arg_index, size_t arg_size, const void *arg_value) { return 0; }
    cl_int clEnqueueReadBuffer(cl_command_queue command_queue, cl_mem buffer, cl_bool blocking_read, size_t offset, size_t size, void *ptr, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clEnqueueWriteBuffer(cl_command_queue command_queue, cl_mem buffer, cl_bool blocking_write, size_t offset, size_t size, const void *ptr, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clEnqueueNDRangeKernel(cl_command_queue command_queue, cl_kernel kernel, cl_uint work_dim, const size_t *global_work_offset, const size_t *global_work_size, const size_t *local_work_size, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clFinish(cl_command_queue command_queue) { return 0; }
    cl_int clGetEventInfo(cl_event event, cl_event_info param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return 0; }
    cl_int clGetEventProfilingInfo(cl_event event, cl_profiling_info param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return 0; }
    cl_int clReleaseEvent(cl_event event) { return 0; }
    cl_int clWaitForEvents(cl_uint num_events, const cl_event *event_list) { return 0; }
    cl_int clGetKernelWorkGroupInfo(cl_kernel kernel, cl_device_id device, cl_kernel_work_group_info param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return 0; }
    
    // Missing symbols from the second build attempt
    cl_mem clCreateImage(cl_context context, cl_mem_flags flags, const cl_image_format *image_format, const cl_image_desc *image_desc, void *host_ptr, cl_int *errcode_ret) { return 0; }
    cl_mem clCreateSubBuffer(cl_mem buffer, cl_mem_flags flags, cl_buffer_create_type buffer_create_type, const void *buffer_create_info, cl_int *errcode_ret) { return 0; }
    cl_int clEnqueueBarrierWithWaitList(cl_command_queue command_queue, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clEnqueueCopyBuffer(cl_command_queue command_queue, cl_mem src_buffer, cl_mem dst_buffer, size_t src_offset, size_t dst_offset, size_t size, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clEnqueueFillBuffer(cl_command_queue command_queue, cl_mem buffer, const void *pattern, size_t pattern_size, size_t offset, size_t size, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clEnqueueMarkerWithWaitList(cl_command_queue command_queue, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return 0; }
    cl_int clFlush(cl_command_queue command_queue) { return 0; }
}
