import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { employeeApi } from '@/api/employees';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { useAuth } from '@/contexts/AuthContext';

export function MyTeamPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const employeeId = user?.employeeId ?? null;

  const team = useQuery({
    queryKey: ['employees', employeeId, 'team'],
    queryFn: () => employeeApi.team(employeeId as number),
    enabled: Boolean(employeeId),
  });

  if (!employeeId) {
    return <Alert severity="info">This account is not linked to an employee record.</Alert>;
  }

  const members = team.data ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Management"
        title="My team"
        description="People who report directly to you. Work-log review and performance actions appear here as those phases land."
      />

      <Card>
        {team.isLoading ? (
          <TableSkeleton rows={5} columns={5} />
        ) : members.length === 0 ? (
          <EmptyState
            title="No direct reports"
            description="When employees are assigned to report to you, they appear here."
          />
        ) : (
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Employee</TableCell>
                  <TableCell>Designation</TableCell>
                  <TableCell>Department</TableCell>
                  <TableCell>Joined</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {members.map((member) => (
                  <TableRow
                    key={member.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/employees/${member.id}`)}
                  >
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                        <Avatar
                          src={member.profileImageUrl ?? undefined}
                          sx={{ width: 30, height: 30, fontSize: 11.5, bgcolor: 'primary.main' }}
                        >
                          {member.fullName
                            .split(' ')
                            .slice(0, 2)
                            .map((part) => part[0])
                            .join('')}
                        </Avatar>
                        <Box>
                          <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{member.fullName}</Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {member.employeeCode}
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>{member.designationName ?? '—'}</TableCell>
                    <TableCell>{member.departmentName ?? '—'}</TableCell>
                    <TableCell className="tabular">
                      {new Date(member.joiningDate).toLocaleDateString('en-GB', {
                        day: '2-digit',
                        month: 'short',
                        year: 'numeric',
                      })}
                    </TableCell>
                    <TableCell>
                      <StatusChip status={member.status} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </Box>
  );
}
